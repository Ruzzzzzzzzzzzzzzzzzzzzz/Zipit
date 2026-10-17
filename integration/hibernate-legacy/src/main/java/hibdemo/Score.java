package hibdemo;

import java.util.List;

/** Plain application logic: renamed and virtualized by Zipit. */
final class Score {

    private Score() {
    }

    static int totalAge(List<Person> people) {
        int total = 0;
        for (Person person : people) {
            total += person.getAge();
        }
        return total;
    }

    static String label(int age) {
        if (age >= 50) {
            return "senior";
        }
        return "adult";
    }
}
