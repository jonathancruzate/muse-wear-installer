import com.jonathancruzate.metamusewearinstaller.Protocol;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * ProtocolTest — plain-JVM assertions for the wear-install wire
 * contract (same discipline as the phone's ShellAllowlistTest: no
 * JUnit, run with javac/java, PASS/FAIL per case, exit code carries
 * the verdict).
 *
 *   javac -cp ../app/src -d /tmp/pt . ; java -cp /tmp/pt:. ProtocolTest
 *
 * Covers: SHA-256 known vectors, offer/status JSON round-trips
 * through the flat parser (including escaping), malformed-input
 * behavior, and the terminal-stage set.
 */
public class ProtocolTest {
    static int pass = 0, fail = 0;

    static void expect(String name, boolean cond) {
        if (cond) { pass++; System.out.println("PASS  " + name); }
        else { fail++; System.out.println("FAIL  " + name); }
    }

    public static void main(String[] args) throws Exception {
        // SHA-256 known vectors
        expect("sha256 empty",
                Protocol.sha256Hex(new ByteArrayInputStream(new byte[0]))
                        .equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        expect("sha256 'abc'",
                Protocol.sha256Hex(new ByteArrayInputStream(
                        "abc".getBytes(StandardCharsets.UTF_8)))
                        .equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        byte[] big = new byte[200_000];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i * 31 + 7);
        String bigHash = Protocol.sha256Hex(new ByteArrayInputStream(big));
        expect("sha256 streaming 200KB deterministic",
                bigHash.length() == 64
                        && bigHash.equals(Protocol.sha256Hex(
                                new ByteArrayInputStream(big))));

        // Offer round-trip
        String offer = Protocol.offerJson("job-123", "Swiss Army v6.2",
                "ab".repeat(32), 591_032);
        Map<String, String> m = Protocol.parseFlat(offer);
        expect("offer job_id", "job-123".equals(m.get("job_id")));
        expect("offer label", "Swiss Army v6.2".equals(m.get("label")));
        expect("offer sha256", "ab".repeat(32).equals(m.get("sha256")));
        expect("offer size", "591032".equals(m.get("size")));
        expect("offer protocol", "1".equals(m.get("protocol")));

        // Status round-trip with hostile detail text
        String status = Protocol.statusJson("job-9", "Face \"X\" \\ test",
                "failed", "status=8 msg: \"bad\" \\path\nnewline", 1731283200000L);
        Map<String, String> s = Protocol.parseFlat(status);
        expect("status job", "job-9".equals(s.get("job_id")));
        expect("status label escaped round-trip",
                "Face \"X\" \\ test".equals(s.get("label")));
        expect("status stage", "failed".equals(s.get("stage")));
        expect("status detail escaped round-trip",
                "status=8 msg: \"bad\" \\path\nnewline".equals(s.get("detail")));
        expect("status ts", "1731283200000".equals(s.get("ts")));

        // Parser tolerance: reordered fields + whitespace
        Map<String, String> r = Protocol.parseFlat(
                "{ \"stage\" : \"installed\" , \"job_id\" : \"j1\" }");
        expect("parser reorder/ws", "installed".equals(r.get("stage"))
                && "j1".equals(r.get("job_id")));

        // Malformed input fails closed (empty map, no exception)
        expect("parser garbage", Protocol.parseFlat("not json").isEmpty());
        expect("parser null", Protocol.parseFlat(null).isEmpty());
        expect("parser truncated",
                Protocol.parseFlat("{\"job_id\":\"x").isEmpty()
                        || Protocol.parseFlat("{\"job_id\":\"x")
                                .get("job_id") == null);

        // Terminal stages
        expect("terminal installed", Protocol.isTerminal("installed"));
        expect("terminal failed", Protocol.isTerminal("failed"));
        expect("terminal verify_failed", Protocol.isTerminal("verify_failed"));
        expect("terminal dismissed", Protocol.isTerminal("dismissed"));
        expect("non-terminal verified", !Protocol.isTerminal("verified"));
        expect("non-terminal installing", !Protocol.isTerminal("installing"));

        System.out.println("----");
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
