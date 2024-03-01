import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final HttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    public CrptApi(int requestLimit, TimeUnit timeUnit) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.baseUrl = "https://ismp.crpt.ru/api/v3/lk/documents";
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        long period = timeUnit.toMillis(1);
        scheduler.scheduleAtFixedRate(() -> {
            int permitsToRelease = requestLimit - semaphore.availablePermits();
            semaphore.release(permitsToRelease);
        }, period, period, TimeUnit.MILLISECONDS);
    }

    @Getter
    @Setter
    public static class Document {
            private Description description;
            private String doc_id;
            private String doc_status;
            private String doc_type;
            private boolean importRequest;
            private String owner_inn;
            private String participant_inn;
            private String producer_inn;
            private String production_date;
            private String production_type;
            private List<Product> products;
            private String reg_date;
            private String reg_number;

            @Getter
            @Setter
            public static class Description {
                private String participantInn;
            }

            @Getter
            @Setter
            public static class Product {
                private String certificate_document;
                private String certificate_document_date;
                private String certificate_document_number;
                private String owner_inn;
                private String producer_inn;
                private String production_date;
                private String tnved_code;
                private String uit_code;
                private String uitu_code;
            }
    }

    public void createDocument(Document document, String signature) {
        try {
            semaphore.acquire();

            String jsonDocument = gson.toJson(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Документ успешно создан: " + response.body());
            } else {
                System.out.println("Ошибка при создании документа: " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}