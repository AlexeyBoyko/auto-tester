import io.restassured.path.xml.XmlPath;
import io.restassured.response.ValidatableResponse;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.DoubleStream;

import static io.restassured.RestAssured.given;
import static io.restassured.path.xml.XmlPath.from;

public class ApiTest {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    final String requestDate = LocalDateTime.now().minusDays(1).format(formatter);// устанавливаем вчерашнюю дату, т.к. за текущую (сегодн) отчёт будет готов только вечером
    final String URL = "http://iss.moex.com/iss/history/engines/stock/markets/shares/boards/tqbr/securities.xml?date="
            + requestDate;

    ValidatableResponse validatableResponse;
    XmlPath xmlPath;
    final String
            subPath1="document.data[0].rows.row"
            ,subPath2 = "find{ it.@SHORTNAME == 'Аэрофлот' }";

    @Before
    public void setup(){
        validatableResponse =
                given()
                .log().uri()
                .when()
                .get(URL)
                .then()
                .log().ifError()
                .statusCode(HttpStatus.SC_OK) //200
                .rootPath(subPath1);

        xmlPath = from(validatableResponse.extract().asString());
        xmlPath.setRootPath(subPath1);
    }
    @Test
    public void test1() {
        String closeValueForAeroflot =
                validatableResponse.appendRootPath(subPath2)
                .body("@TRADEDATE",is(requestDate)) // сверяем с датой которая была "на входе"
                .extract()
                .path(subPath1 +"." + subPath2 + ".@CLOSE"); // к сожалению, path() не учитывает rootPath установленный до этого, поэтому приходится пересобирать путь заново...
        System.out.println("CLOSE value for Aeroflot =" + closeValueForAeroflot);
    }

    @Test
    public void test2() {

        // через groovy
        double sumOfHigh = xmlPath.getDouble("findAll{ it.@HIGH != '' }.collect { it['@HIGH'].text().toDouble() }.sum()");
        System.out.println("SUM of HIGH=" + sumOfHigh);

        // с применением stream-ов
        List<String> rows = xmlPath.getList("@HIGH");
        DoubleStream ds = rows.stream().filter(row -> !(row.isEmpty())).flatMapToDouble((row -> DoubleStream.of(Double.parseDouble(row))));
        double sumOfHigh2 = ds.sum();

        // сверяем полученные результаты
        assertThat(sumOfHigh, is(sumOfHigh2));

        // проверка вычисления через xPath (правда, не нашёл как вычисленную таким образом сумму можно вытащить - получается только сверить)
        validatableResponse
        .body(hasXPath("sum(/document/data[1]/rows/row[@HIGH and string-length(normalize-space(@HIGH))]/@HIGH)", equalTo(String.valueOf(sumOfHigh)))).toString();
    }
}



