package org.example;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private static final Logger logger = LoggerFactory.getLogger(CrptApi.class);

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final Lock lock = new ReentrantLock();
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.client = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);

        long period = timeUnit.toMillis(1);
        this.scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                semaphore.release(requestLimit - semaphore.availablePermits());
            } catch (Exception e) {
                logger.error("Ошибка при обновлении лимита запросов", e);
            } finally {
                lock.unlock();
            }
        }, period, period, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document document, String signature) {
        try {
            semaphore.acquire();
            String jsonDocument = objectMapper.writeValueAsString(document);
            RequestBody body = RequestBody.create(jsonDocument, MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .header("Signature", signature)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    logger.error("Ошибка создания документа: {} - {}", response.code(), responseBody);
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    logger.info("Документ успешно создан: {}", responseBody);
                }
            } catch (IOException e) {
                logger.error("Ошибка при выполнении запроса", e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Восстанавливаем статус прерывания
            logger.error("Поток был прерван во время ожидания разрешения семафора", e);
        } catch (IOException e) {
            logger.error("Ошибка при сериализации документа в JSON", e);
        }
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public Document(Description description, String doc_id, String doc_status, String doc_type,
                        boolean importRequest, String owner_inn, String participant_inn, String producer_inn,
                        String production_date, String production_type, Product[] products, String reg_date, String reg_number) {
            this.description = description;
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.doc_type = doc_type;
            this.importRequest = importRequest;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.products = products;
            this.reg_date = reg_date;
            this.reg_number = reg_number;
        }

        public static class Description {
            public String participantInn;

            public Description(String participantInn) {
                this.participantInn = participantInn;
            }
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;

            public Product(String certificate_document, String certificate_document_date, String certificate_document_number,
                           String owner_inn, String producer_inn, String production_date,
                           String tnved_code, String uit_code, String uitu_code) {
                this.certificate_document = certificate_document;
                this.certificate_document_date = certificate_document_date;
                this.certificate_document_number = certificate_document_number;
                this.owner_inn = owner_inn;
                this.producer_inn = producer_inn;
                this.production_date = production_date;
                this.tnved_code = tnved_code;
                this.uit_code = uit_code;
                this.uitu_code = uitu_code;
            }
        }
    }
}
