package iteration_2;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import testDataGenerator.TestDataGenerator;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DepositsUserTest {

    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter()));
    }

    // Пополнение счета. Положительные сценарии
    @ParameterizedTest
    @ValueSource(doubles = {5000.00, 4999.99, 0.01})
    public void userCanDepositMoneyIntoTheAccountValidTest(double balance) {
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

        // Получаем токен пользователя
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

        // Проверка что аккаунт создался у пользователя
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("id", hasItem(createdAccountId))
                .body("find { it.id == %s }.balance".formatted(createdAccountId), equalTo(0.0F));

        // Проверка, что до пополнения транзакций нет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(createdAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(0));

        // Пополнение аккаунта
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": %s
                        }
                        """.formatted(createdAccountId, balance))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        // Проверка успешной транзакции пополнения
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(createdAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(1))
                .body("[0].type", equalTo("DEPOSIT"))
                .body("[0].amount", equalTo((float) balance));
    }

    // Пополнение счета. Отрицательные тесты
    @ParameterizedTest
    @ValueSource(doubles = {5000.01, 0, -1})
    public void userIsUnableToDepositMoneyIntoTheAccountInValidTest(double balance) {
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

        // Получаем токен пользователя
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

        // Проверка что аккаунт создался у пользователя
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("id", hasItem(createdAccountId))
                .body("find { it.id == %s }.balance".formatted(createdAccountId), equalTo(0.0F));

        // Пополнение аккаунта невалидной суммой
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": %s,
                          "balance": %s
                        }
                        """.formatted(createdAccountId, balance))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

        // Проверка что транзакция пополнения не создалась
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(createdAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(0));
    }

    // Пополнение несуществующего счета. Отрицательный тест.
    @Test
    public void userDepositsToNonExistingAccountInValidTest() {
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

        // Получаем токен пользователя
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

        // Проверка что реальный аккаунт создался у пользователя
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("id", hasItem(createdAccountId))
                .body("find { it.id == %s }.balance".formatted(createdAccountId), equalTo(0.0F));

        // Пополнение несуществующего аккаунта
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "id": 999999999,
                          "balance": 100
                        }
                        """)
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_FORBIDDEN);

        // Проверка что транзакций на реальном счете нет
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/accounts/%s/transactions".formatted(createdAccountId))
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("size()", equalTo(0));
    }
}