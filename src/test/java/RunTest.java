import java.io.IOException;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.Test;
import org.workrideclub.Utils;

public class RunTest {
    private static final Logger logger = Logger.getLogger(RunTest.class.getName());

    @Test
    public void runTest() {
        while (true) {
            Utils.removeBrokenStatus();
            Utils.addNewDriverTravelTime();
            Utils.processFirstUnmatched();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}