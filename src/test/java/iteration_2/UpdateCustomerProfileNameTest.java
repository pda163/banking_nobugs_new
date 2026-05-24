package iteration_2;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import testDataGenerator.TestDataGenerator;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class UpdateCustomerProfileNameTest {

    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter()));
    }

    // Изменение имени профиля у аккаунта. Положительный тест
    @Test
    public void userCanEditTheProfileNameTest() {

        String username = TestDataGenerator.generateUsername();
        String password = TestDataGenerator.generatePassword();
        String newName = TestDataGenerator.generateProfileName();

        // Создание пользователя с корректными данными
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
                .statusCode(HttpStatus.SC_CREATED)
                .body("username", Matchers.equalTo(username))
                .body("password", Matchers.not(Matchers.equalTo(password)))
                .body("role", Matchers.equalTo("USER"))
                .extract()
                .path("id");

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

        // Проверка имени профиля до изменения
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("username", Matchers.equalTo(username))
                .body("name", Matchers.nullValue());

        // Изменение имени профиля
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(newName))
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("message", Matchers.equalTo("Profile updated successfully"))
                .body("customer.username", Matchers.equalTo(username))
                .body("customer.name", Matchers.equalTo(newName));

        // Проверка имени профиля после изменения
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("username", Matchers.equalTo(username))
                .body("name", Matchers.equalTo(newName));
    }

    // Изменение имени профиля невалидными данными
    @ParameterizedTest
    @ValueSource(strings = {"Daniel", "1234", "Ivanov_Ivan", "", "  "})
    public void userCanNotEditTheProfileNameInvalidateTest(String name) {

        String username = TestDataGenerator.generateUsername();
        String password = TestDataGenerator.generatePassword();

        // Создание пользователя с корректными данными
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
                .statusCode(HttpStatus.SC_CREATED)
                .body("username", Matchers.equalTo(username))
                .body("password", Matchers.not(Matchers.equalTo(password)))
                .body("role", Matchers.equalTo("USER"))
                .extract()
                .path("id");

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

        // Проверка имени профиля до изменения
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("username", Matchers.equalTo(username))
                .body("name", Matchers.nullValue());

        // Изменение имени профиля невалидными данными
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(name))
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(equalTo("Name must contain two words with letters only"));

        // Проверка что имя профиля не изменилось после невалидного запроса
        given()
                .header("Authorization", userAuthHeader)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("username", Matchers.equalTo(username))
                .body("name", Matchers.nullValue());
    }
}
