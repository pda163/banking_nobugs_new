package iteration_2;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import testDataGenerator.TestDataGenerator;

import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class TransferMoneyTest {

    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(
                        new RequestLoggingFilter(),
                        new ResponseLoggingFilter()
                )
        );
    }

    // Перевод денег между двумя пользователями
    @ParameterizedTest
    @ValueSource(doubles = {10000.00, 9999.99, 0.01})
    public void userCanTransferMoneyToTheAccountTest(double amount) {

        Integer senderAccountId = null;
        Integer receiverAccountId = null;
        String senderAuthHeader = null;
        String receiverAuthHeader = null;

        for (int userNumber = 1; userNumber <= 2; userNumber++) {

            String username = TestDataGenerator.generateUsername();
            String password = TestDataGenerator.generatePassword();

            // Создание пользователя
            given()
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                    .body("""
                            {
                              "password": "%s",
                              "username": "%s",
                              "role": "USER"
                            }
                            """.formatted(password, username))
                    .post("http://localhost:4111/api/v1/admin/users")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_CREATED);

            // Авторизация
            String userAuthHeader = given()
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .body("""
                            {
                              "password": "%s",
                              "username": "%s"
                            }
                            """.formatted(password, username))
                    .post("http://localhost:4111/api/v1/auth/login")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_OK)
                    .extract()
                    .header("Authorization");

            // Создание аккаунта
            Integer createdAccountId = given()
                    .header("Authorization", userAuthHeader)
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .post("http://localhost:4111/api/v1/accounts")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_CREATED)
                    .extract()
                    .path("id");

            // Сохраняем id аккаунтов
            if (userNumber == 1) {
                senderAccountId = createdAccountId;
                senderAuthHeader = userAuthHeader;
            } else {
                receiverAccountId = createdAccountId;
                receiverAuthHeader = userAuthHeader;
            }

            // Пополнение на 15000
            for (int i = 0; i < 3; i++) {
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "id": %s,
                                  "balance": 5000
                                }
                                """.formatted(createdAccountId))
                        .post("http://localhost:4111/api/v1/accounts/deposit")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK);
            }

            // Проверка транзакций
            given()
                    .header("Authorization", userAuthHeader)
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(createdAccountId))
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_OK)
                    .body("size()", equalTo(3))
                    .body("type", everyItem(equalTo("DEPOSIT")))
                    .body("amount", everyItem(equalTo(5000.0F)))
                    .body("relatedAccountId", everyItem(equalTo(createdAccountId)));

            // Проверка баланса в профиле до перевода
            given()
                    .header("Authorization", userAuthHeader)
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .get("http://localhost:4111/api/v1/customer/profile")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_OK)
                    .body("accounts.find { it.id == %s }.balance".formatted(createdAccountId), equalTo(15000.0F));
        }

        // Перевод денег
        given()
                .header("Authorization", senderAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %s,
                          "receiverAccountId": %s,
                          "amount": %s
                        }
                        """.formatted(senderAccountId, receiverAccountId, amount))
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        // Проверка транзакций отправителя после перевода
        given()
                .header("Authorization", senderAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(senderAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(4))
                .body("type", hasItem("TRANSFER_OUT"))
                .body("find { it.type == 'TRANSFER_OUT' }.amount", equalTo((float) amount))
                .body("find { it.type == 'TRANSFER_OUT' }.relatedAccountId", equalTo(receiverAccountId));

        // Проверка транзакций получателя после перевода
        given()
                .header("Authorization", receiverAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(receiverAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(4))
                .body("type", hasItem("TRANSFER_IN"))
                .body("find { it.type == 'TRANSFER_IN' }.amount", equalTo((float) amount))
                .body("find { it.type == 'TRANSFER_IN' }.relatedAccountId", equalTo(senderAccountId));

        // Проверка баланса отправителя в профиле после перевода
        given()
                .header("Authorization", senderAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("accounts.find { it.id == %s }.balance".formatted(senderAccountId), equalTo((float) (15000 - amount)));

        // Проверка баланса получателя в профиле после перевода
        given()
                .header("Authorization", receiverAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("accounts.find { it.id == %s }.balance".formatted(receiverAccountId), equalTo((float) (15000 + amount)));
    }

    private static Stream<Arguments> invalidTransferAmounts() {
        return Stream.of(
                Arguments.of(0.0, "Transfer amount must be at least 0.01"),
                Arguments.of(-1.0, "Transfer amount must be at least 0.01"),
                Arguments.of(10000.01, "Transfer amount cannot exceed 10000"),
                Arguments.of(1000000.0, "Transfer amount cannot exceed 10000")
        );
    }

    // Негативные проверки переводов
    @ParameterizedTest
    @MethodSource("invalidTransferAmounts")
    public void userCanNotTransferMoneyToTheAccountInValidTest(double amount,
                                                               String expectedMessage) {

        Integer senderAccountId = null;
        Integer receiverAccountId = null;
        String senderAuthHeader = null;
        String receiverAuthHeader = null;

        for (int userNumber = 1; userNumber <= 2; userNumber++) {

            String username = TestDataGenerator.generateUsername();
            String password = TestDataGenerator.generatePassword();

            // Создание пользователя
            given()
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                    .body("""
                            {
                              "password": "%s",
                              "username": "%s",
                              "role": "USER"
                            }
                            """.formatted(password, username))
                    .post("http://localhost:4111/api/v1/admin/users")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_CREATED);

            // Авторизация
            String userAuthHeader = given()
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .body("""
                            {
                              "password": "%s",
                              "username": "%s"
                            }
                            """.formatted(password, username))
                    .post("http://localhost:4111/api/v1/auth/login")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_OK)
                    .extract()
                    .header("Authorization");

            // Создание аккаунта
            Integer createdAccountId = given()
                    .header("Authorization", userAuthHeader)
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .post("http://localhost:4111/api/v1/accounts")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_CREATED)
                    .extract()
                    .path("id");

            // Сохраняем id аккаунтов
            if (userNumber == 1) {
                senderAccountId = createdAccountId;
                senderAuthHeader = userAuthHeader;
            } else {
                receiverAccountId = createdAccountId;
                receiverAuthHeader = userAuthHeader;
            }

            // Пополнение на 15000
            for (int i = 0; i < 3; i++) {
                given()
                        .header("Authorization", userAuthHeader)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body("""
                                {
                                  "id": %s,
                                  "balance": 5000
                                }
                                """.formatted(createdAccountId))
                        .post("http://localhost:4111/api/v1/accounts/deposit")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK);
            }

            // Проверка транзакций
            given()
                    .header("Authorization", userAuthHeader)
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(createdAccountId))
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_OK)
                    .body("size()", equalTo(3))
                    .body("type", everyItem(equalTo("DEPOSIT")))
                    .body("amount", everyItem(equalTo(5000.0F)))
                    .body("relatedAccountId", everyItem(equalTo(createdAccountId)));

            // Проверка баланса в профиле до перевода
            given()
                    .header("Authorization", userAuthHeader)
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .get("http://localhost:4111/api/v1/customer/profile")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_OK)
                    .body("accounts.find { it.id == %s }.balance".formatted(createdAccountId), equalTo(15000.0F));
        }

        // Перевод денег невалидной суммой
        given()
                .header("Authorization", senderAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %s,
                          "receiverAccountId": %s,
                          "amount": %s
                        }
                        """.formatted(senderAccountId, receiverAccountId, amount))
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(equalTo(expectedMessage));

        // Проверка транзакций отправителя после неуспешного перевода
        given()
                .header("Authorization", senderAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(senderAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(3))
                .body("type", not(hasItem("TRANSFER_OUT")));

        // Проверка транзакций получателя после неуспешного перевода
        given()
                .header("Authorization", receiverAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(receiverAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(3))
                .body("type", not(hasItem("TRANSFER_IN")));

        // Проверка что баланс отправителя в профиле не изменился
        given()
                .header("Authorization", senderAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("accounts.find { it.id == %s }.balance".formatted(senderAccountId), equalTo(15000.0F));

        // Проверка что баланс получателя в профиле не изменился
        given()
                .header("Authorization", receiverAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("accounts.find { it.id == %s }.balance".formatted(receiverAccountId), equalTo(15000.0F));
    }

    // Перевод на несуществующий аккаунт
    @Test
    public void userCannotTransferMoneyToNonExistingAccountTest() {

        String username = TestDataGenerator.generateUsername();
        String password = TestDataGenerator.generatePassword();

        // Создание пользователя
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body("""
                        {
                          "password": "%s",
                          "username": "%s",
                          "role": "USER"
                        }
                        """.formatted(password, username))
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // Авторизация
        String authHeader = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "password": "%s",
                          "username": "%s"
                        }
                        """.formatted(password, username))
                .post("http://localhost:4111/api/v1/auth/login")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .header("Authorization");

        // Создание аккаунта
        Integer senderAccountId = given()
                .header("Authorization", authHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .path("id");

        // Пополнение счета
        given()
                .header("Authorization", authHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        // Проверка транзакций
        given()
                .header("Authorization", authHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(senderAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(1))
                .body("[0].type", equalTo("DEPOSIT"))
                .body("[0].amount", equalTo(5000.0F))
                .body("[0].relatedAccountId", equalTo(senderAccountId));

        // Проверка баланса в профиле до перевода
        given()
                .header("Authorization", authHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("accounts.find { it.id == %s }.balance".formatted(senderAccountId), equalTo(5000.0F));

        // Перевод на несуществующий аккаунт
        given()
                .header("Authorization", authHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %s,
                          "receiverAccountId": 999999,
                          "amount": 100
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(equalTo("Invalid transfer: insufficient funds or invalid accounts"));

        // Проверка транзакций после неуспешного перевода
        given()
                .header("Authorization", authHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(senderAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(1))
                .body("type", not(hasItem("TRANSFER_OUT")));

        // Проверка что баланс в профиле не изменился
        given()
                .header("Authorization", authHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("accounts.find { it.id == %s }.balance".formatted(senderAccountId), equalTo(5000.0F));
    }

    // Перевод между своими счетами
    @Test
    public void userCanTransferMoneyBetweenOwnAccountsTest() {

        String username = TestDataGenerator.generateUsername();
        String password = TestDataGenerator.generatePassword();

        // Создание пользователя
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body("""
                        {
                          "password": "%s",
                          "username": "%s",
                          "role": "USER"
                        }
                        """.formatted(password, username))
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        // Авторизация
        String userAuthHeader = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "password": "%s",
                          "username": "%s"
                        }
                        """.formatted(password, username))
                .post("http://localhost:4111/api/v1/auth/login")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .header("Authorization");

        // Создание первого счета
        Integer firstAccountId = given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .path("id");

        // Создание второго счета
        Integer secondAccountId = given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .path("id");

        // Пополнение первого счета
        for (int i = 0; i < 3; i++) {
            given()
                    .header("Authorization", userAuthHeader)
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .body("""
                            {
                              "id": %s,
                              "balance": 5000
                            }
                            """.formatted(firstAccountId))
                    .post("http://localhost:4111/api/v1/accounts/deposit")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_OK);
        }

        // Проверка транзакций первого счета
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(firstAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(3))
                .body("type", everyItem(equalTo("DEPOSIT")))
                .body("amount", everyItem(equalTo(5000.0F)))
                .body("relatedAccountId", everyItem(equalTo(firstAccountId)));

        // Пополнение второго счета
        for (int i = 0; i < 3; i++) {
            given()
                    .header("Authorization", userAuthHeader)
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .body("""
                            {
                              "id": %s,
                              "balance": 5000
                            }
                            """.formatted(secondAccountId))
                    .post("http://localhost:4111/api/v1/accounts/deposit")
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_OK);
        }

        // Проверка транзакций второго счета
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(secondAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(3))
                .body("type", everyItem(equalTo("DEPOSIT")))
                .body("amount", everyItem(equalTo(5000.0F)))
                .body("relatedAccountId", everyItem(equalTo(secondAccountId)));

        // Проверка баланса в профиле до перевода
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("accounts.find { it.id == %s }.balance".formatted(firstAccountId), equalTo(15000.0F))
                .body("accounts.find { it.id == %s }.balance".formatted(secondAccountId), equalTo(15000.0F));

        // Перевод
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "senderAccountId": %s,
                          "receiverAccountId": %s,
                          "amount": 1000
                        }
                        """.formatted(firstAccountId, secondAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        // Проверка транзакций первого счета после перевода
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(firstAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(4))
                .body("type", hasItem("TRANSFER_OUT"))
                .body("find { it.type == 'TRANSFER_OUT' }.amount", equalTo(1000.0F))
                .body("find { it.type == 'TRANSFER_OUT' }.relatedAccountId", equalTo(secondAccountId));

        // Проверка транзакций второго счета после перевода
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(secondAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(4))
                .body("type", hasItem("TRANSFER_IN"))
                .body("find { it.type == 'TRANSFER_IN' }.amount", equalTo(1000.0F))
                .body("find { it.type == 'TRANSFER_IN' }.relatedAccountId", equalTo(firstAccountId));

        // Проверка баланса в профиле после перевода
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("accounts.find { it.id == %s }.balance".formatted(firstAccountId), equalTo(14000.0F))
                .body("accounts.find { it.id == %s }.balance".formatted(secondAccountId), equalTo(16000.0F));
    }
}