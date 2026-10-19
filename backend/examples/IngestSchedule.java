import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ou.capstone.demo.ScheduleDemoData;
import java.nio.file.Path;
import java.time.LocalDate;

/** Run from backend/ with the compiled backend and its dependencies on the classpath. */
public class IngestSchedule {
  public static void main(String[] args) throws Exception {
    if (args.length != 4) {
      throw new IllegalArgumentException(
          "Usage: IngestSchedule.java <Schedule.json> <new-output-directory> <season> <before-date>");
    }
    var data =
        ScheduleDemoData.ingest(
            Path.of(args[0]), Integer.parseInt(args[2]), LocalDate.parse(args[3]));
    var mapper =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    data.write(Path.of(args[1]), mapper);
    System.out.printf(
        "Ingested %d teams, %d historical games, %d prediction candidates; skipped %d source rows.%n",
        data.teams().size(), data.history().size(), data.games().size(), data.skipped().size());
    System.out.printf(
        "History strictly before %s; snapshot=%s%n", data.cutoff(), data.dataSnapshotId());
    System.out.println("Artifacts: " + Path.of(args[1]).toAbsolutePath());
    System.out.println("Skipped-row details are in dataset.json under skipped.");
  }
}
