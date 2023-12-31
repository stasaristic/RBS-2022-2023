package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.config.AuditLogger;
import com.zuehlke.securesoftwaredevelopment.domain.Genre;
import com.zuehlke.securesoftwaredevelopment.domain.Movie;
import com.zuehlke.securesoftwaredevelopment.domain.NewMovie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MovieRepository {

    private static final Logger LOG = LoggerFactory.getLogger(MovieRepository.class);
    private static final AuditLogger auditLogger = AuditLogger.getAuditLogger(MovieRepository.class);

    private DataSource dataSource;

    public MovieRepository(DataSource dataSource) {

        this.dataSource = dataSource;
    }

    public List<Movie> getAll() {
        List<Movie> movieList = new ArrayList<>();
        String query = "SELECT id, title, description FROM movies";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                Movie movie = createMovieFromResultSet(rs);
                movieList.add(movie);
            }
            LOG.info("Movies successfully loaded.");
        } catch (SQLException e) {
            LOG.warn("Unsuccessful movie load; SQL exception happened: " + e);
        }
        return movieList;
    }

    public List<Movie> search(String searchTerm) {
        List<Movie> movieList = new ArrayList<>();
        String query = "SELECT DISTINCT m.id, m.title, m.description FROM movies m, movies_to_genres mg, genres g" +
                " WHERE m.id = mg.movieId" +
                " AND mg.genreId = g.id" +
                " AND (UPPER(m.title) like UPPER('%" + searchTerm + "%')" +
                " OR UPPER(g.name) like UPPER('%" + searchTerm + "%'))";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                movieList.add(createMovieFromResultSet(rs));
            }
            LOG.info("Movies search was successful.");
        }catch (SQLException e) {
            LOG.warn("Unsuccessful movie search; SQL exception happened: " + e);
        }
        return movieList;
    }

    public Movie get(int movieId, List<Genre> genreList) {
        String query = "SELECT id, title, description FROM movies WHERE id = " + movieId;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                Movie movie = createMovieFromResultSet(rs);
                List<Genre> movieGenres = new ArrayList<>();
                String query2 = "SELECT movieId, genreId FROM movies_to_genres WHERE movieId = " + movieId;
                ResultSet rs2 = statement.executeQuery(query2);
                while (rs2.next()) {
                    Genre genre = genreList.stream().filter(g -> {
                        try {
                            return g.getId() == rs2.getInt(2);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }).findFirst().get();
                    movieGenres.add(genre);
                }
                movie.setGenres(movieGenres);
                LOG.info("Getting a singular movie with id: " + movieId + " was successful.");
                return movie;
            }
        } catch (SQLException e) {
            LOG.warn("Getting a singular movie with id: " + movieId + " was unsuccessful. " +
                    "Due to SQL exception: " + e);
        }

        return null;
    }

    public long create(NewMovie movie, List<Genre> genresToInsert) {
        String query = "INSERT INTO movies(title, description) VALUES(?, ?)";
        long id = 0;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        ) {
            statement.setString(1, movie.getTitle());
            statement.setString(2, movie.getDescription());
            statement.executeUpdate();
            LOG.info("New movie successfully created with name: " + movie.getTitle());
            ResultSet generatedKeys = statement.getGeneratedKeys();
            if (generatedKeys.next()) {
                id = generatedKeys.getLong(1);
                long finalId = id;
                genresToInsert.stream().forEach(genre -> {
                    String query2 = "INSERT INTO movies_to_genres(movieId, genreId) VALUES (?, ?)";
                    try (PreparedStatement statement2 = connection.prepareStatement(query2);
                    ) {
                        statement2.setInt(1, (int) finalId);
                        statement2.setInt(2, genre.getId());
                        LOG.info("New movie successfully created with adequate genre in the database with genre id: " + genre.getId());
                        statement2.executeUpdate();
                    } catch (SQLException e) {
                        LOG.warn("Unsuccessfully added movie id to the adequate genre id with genre id: " + genre.getId()
                        + " and movie id: " + finalId + " . Due to SQL exception: " + e);
                    }
                });
            }
        } catch (SQLException e) {
            LOG.warn("Movie creation unsuccessful due to SQL exception: " + e);
        }
        return id;
    }

    public void delete(int movieId) {
        String query = "DELETE FROM movies WHERE id = " + movieId;
        String query2 = "DELETE FROM ratings WHERE movieId = " + movieId;
        String query3 = "DELETE FROM comments WHERE movieId = " + movieId;
        String query4 = "DELETE FROM movies_to_genres WHERE movieId = " + movieId;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
        ) {
            statement.executeUpdate(query);
            statement.executeUpdate(query2);
            statement.executeUpdate(query3);
            statement.executeUpdate(query4);
            LOG.info("Movie successfully deleted with cascade; movie id: " + movieId);
        } catch (SQLException e) {
            LOG.warn("Movie unsuccessfully deleted with cascade; movie id: " + movieId);
        }
    }

    private Movie createMovieFromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt(1);
        String title = rs.getString(2);
        String description = rs.getString(3);
        return new Movie(id, title, description, new ArrayList<>());
    }
}
