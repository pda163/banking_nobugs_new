package testDataGenerator;

import java.util.Random;

public class TestDataGenerator {
    private static final Random random = new Random();

    public static String generateUsername() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-";
        int length = 3 + random.nextInt(13);

        StringBuilder username = new StringBuilder();
        for (int i = 0; i < length; i++) {
            username.append(chars.charAt(random.nextInt(chars.length())));
        }

        return username.toString();
    }

    public static String generatePassword() {
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String digits = "0123456789";

        String special = "!@#$%^&+=";

        String all = lower + upper + digits + special;

        StringBuilder password = new StringBuilder();

        password.append(lower.charAt(random.nextInt(lower.length())));
        password.append(upper.charAt(random.nextInt(upper.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));

        for (int i = 4; i < 10; i++) {
            password.append(all.charAt(random.nextInt(all.length())));
        }

        return password.toString();
    }

    public static String generateProfileName() {

        String[] firstNames = {
                "John",
                "Alex",
                "Michael",
                "David",
                "Daniel",
                "James"
        };

        String[] lastNames = {
                "Smith",
                "Johnson",
                "Brown",
                "Wilson",
                "Taylor",
                "Anderson"
        };

        Random random = new Random();

        String firstName = firstNames[random.nextInt(firstNames.length)];
        String lastName = lastNames[random.nextInt(lastNames.length)];

        return firstName + " " + lastName;
    }
}
