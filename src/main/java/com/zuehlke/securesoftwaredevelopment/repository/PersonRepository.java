package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.config.AuditLogger;
import com.zuehlke.securesoftwaredevelopment.config.Entity;
import com.zuehlke.securesoftwaredevelopment.domain.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PersonRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PersonRepository.class);
    private static final AuditLogger auditLogger = AuditLogger.getAuditLogger(PersonRepository.class);

    private DataSource dataSource;

    public PersonRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Person> getAll() {
        List<Person> personList = new ArrayList<>();
        String query = "SELECT id, firstName, lastName, email FROM persons";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                personList.add(createPersonFromResultSet(rs));
            }
            LOG.info("Get all persons successful");
        } catch (SQLException e) {
            LOG.warn("Get all persons unsuccessful due to SQL exception: " + e);
        }
        return personList;
    }

    public List<Person> search(String searchTerm) throws SQLException {
        List<Person> personList = new ArrayList<>();
        String query = "SELECT id, firstName, lastName, email FROM persons WHERE UPPER(firstName) like UPPER('%" + searchTerm + "%')" +
                " OR UPPER(lastName) like UPPER('%" + searchTerm + "%')";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                personList.add(createPersonFromResultSet(rs));
            }
            LOG.info("Person search successful.");
        } catch (SQLException e) {
            LOG.warn("Person search unsuccessful due to SQL exception: " + e);
        }
        return personList;
    }

    public Person get(String personId) {
        String query = "SELECT id, firstName, lastName, email FROM persons WHERE id = " + personId;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                return createPersonFromResultSet(rs);
            }
            LOG.info("Get person by id: " + personId + " successful.");
        } catch (SQLException e) {
            LOG.warn("Get person by id: " + personId + " unsuccessful. Due to SQL exception: " + e);
        }

        return null;
    }

    public void delete(int personId) {
        String query = "DELETE FROM persons WHERE id = " + personId;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
        ) {
            statement.executeUpdate(query);
            AuditLogger.
                    getAuditLogger(PersonRepository.class)
                    .auditChange(new Entity(
                            "person.delete",
                            String.valueOf(personId),
                            "existing person with id: " + personId,
                            "person no longer exists in the database"
                    ));
        } catch (SQLException e) {
            LOG.warn("Person delete unsuccessful due to SQL exception: " + e);
        }
    }

    private Person createPersonFromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt(1);
        String firstName = rs.getString(2);
        String lastName = rs.getString(3);
        String email = rs.getString(4);
        return new Person("" + id, firstName, lastName, email);
    }

    public void update(Person personUpdate) {
        Person personFromDb = get(personUpdate.getId());
        String query = "UPDATE persons SET firstName = ?, lastName = ?, email = ? where id = " + personUpdate.getId();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
        ) {
            String firstName = personUpdate.getFirstName() != null ? personUpdate.getFirstName() : personFromDb.getFirstName();
            String lastName = personUpdate.getLastName() != null ? personUpdate.getLastName() : personFromDb.getLastName();
            String email = personUpdate.getEmail() != null ? personUpdate.getEmail() : personFromDb.getEmail();
            statement.setString(1, firstName);
            statement.setString(2, lastName);
            statement.setString(3, email);
            statement.executeUpdate();
            if (!personFromDb.getFirstName().equals(personUpdate.getFirstName()))
            {
                AuditLogger.
                        getAuditLogger(PersonRepository.class)
                        .auditChange(new Entity(
                                "person.updateFirstName",
                                String.valueOf(personFromDb.getId()),
                                String.valueOf(personFromDb.getFirstName()),
                                String.valueOf(personUpdate.getFirstName())
                        ));
            }
            if (!personFromDb.getLastName().equals(personUpdate.getLastName()))
            {
                AuditLogger.
                        getAuditLogger(PersonRepository.class)
                        .auditChange(new Entity(
                                "person.updateLastName",
                                String.valueOf(personFromDb.getId()),
                                String.valueOf(personFromDb.getLastName()),
                                String.valueOf(personUpdate.getLastName())
                        ));
            }
            if (!personFromDb.getEmail().equals(personUpdate.getEmail()))
            {
                AuditLogger.
                        getAuditLogger(PersonRepository.class)
                        .auditChange(new Entity(
                                "person.updateEmail",
                                String.valueOf(personFromDb.getId()),
                                String.valueOf(personFromDb.getEmail()),
                                String.valueOf(personUpdate.getEmail())
                        ));
            }
        } catch (SQLException e) {
            LOG.warn("Person update unsuccessful due to SQL exception: " + e);
        }
    }
}
