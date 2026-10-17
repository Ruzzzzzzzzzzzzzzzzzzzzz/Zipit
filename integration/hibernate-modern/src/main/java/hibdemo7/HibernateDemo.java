package hibdemo7;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.util.List;

/** Real Hibernate 7 + H2 integration sample (jakarta.persistence). */
final class HibernateDemo {

    private HibernateDemo() {
    }

    public static void main(String[] args) {
        Configuration configuration = new Configuration();
        configuration.addAnnotatedClass(Person.class);
        configuration.setProperty("hibernate.connection.url",
                "jdbc:h2:mem:zipit-modern;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.connection.username", "sa");
        configuration.setProperty("hibernate.connection.password", "");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        configuration.setProperty("hibernate.show_sql", "false");

        SessionFactory factory = configuration.buildSessionFactory();
        try {
            try (Session session = factory.openSession()) {
                session.beginTransaction();
                session.persist(new Person("Ada", 42));
                session.persist(new Person("Linus", 55));
                List<Person> people = session
                        .createQuery("from Person order by age", Person.class)
                        .list();
                for (Person person : people) {
                    System.out.println("modern:" + person.getId() + ":"
                            + person.getName() + ":" + person.getAge()
                            + ":" + Score.label(person.getAge()));
                }
                System.out.println("modern-total:" + Score.totalAge(people));
                session.getTransaction().commit();
            }
        } finally {
            factory.close();
        }
        System.out.println("hibernate-modern-ok");
    }
}
