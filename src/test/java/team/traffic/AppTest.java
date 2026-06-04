package team.traffic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class AppTest {
    @Test
    void mainRunsHelloWorld() {
        assertDoesNotThrow(() -> App.main(new String[0]));
    }
}
