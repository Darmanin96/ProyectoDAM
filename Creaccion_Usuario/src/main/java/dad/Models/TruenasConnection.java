package dad.Models;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;

public class TruenasConnection {

    // Constantes
    private static final String API_ENDPOINT_CREATE_USER = "/api/v2.0/user";
    private static final boolean USE_HTTPS = true;
    private static final String CA_CERT_PATH = "C:\\Users\\darma\\Downloads\\Autoridad_Certificados.crt";
    private final String host = "192.168.1.252";
    private final String apiKey = "3-q35B7QbljPTfeNC5shQWlFyVa8Lc13J0nC4N25ePM7s71QAZHNSrgR4CuzVd7vs5";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Constructor
    public TruenasConnection() {
        System.out.println("DEBUG: TruenasConnection - Constructor llamado. Host: " + host + ", API Key: " + (apiKey != null ? "Presente" : "NULL") + ", CA Path: " + CA_CERT_PATH); // DEBUG
        this.objectMapper = new ObjectMapper();

        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null); // Inicializa un keystore vacío

            // Intentamos cargar el certificado si se proporciona la ruta
            if (CA_CERT_PATH != null && !CA_CERT_PATH.trim().isEmpty()) {
                System.out.println("DEBUG: TruenasConnection - Intentando cargar el certificado CA desde: " + CA_CERT_PATH); // DEBUG
                try (InputStream caCertStream = new FileInputStream(CA_CERT_PATH)) {
                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                    X509Certificate caCertificate = (X509Certificate) certificateFactory.generateCertificate(caCertStream);
                    trustStore.setCertificateEntry("truenas-ca", caCertificate);
                    System.out.println("DEBUG: TruenasConnection - Certificado CA cargado correctamente."); // DEBUG
                } catch (IOException e) {
                    System.err.println("ERROR CRÍTICO: TruenasConnection - Error al cargar el certificado CA desde la ruta: " + CA_CERT_PATH + " - " + e.getMessage()); // DEBUG
                    throw new RuntimeException("No se pudo cargar el certificado CA: " + e.getMessage(), e);
                }
            } else {
                System.out.println("DEBUG: TruenasConnection - No se proporcionó una ruta de certificado CA, utilizando el almacén de confianza predeterminado."); // DEBUG
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore.size() > 0 ? trustStore : null);

            SSLContext sslContext = SSLContext.getInstance("TLSv1.3"); // O TLSv1.2 si es necesario
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

            this.httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // O HTTP_2 si el servidor lo soporta
                    .connectTimeout(Duration.ofSeconds(15))
                    .sslContext(sslContext) // Usa el contexto SSL configurado
                    .build();
            System.out.println("DEBUG: TruenasConnection - HttpClient inicializado."); // DEBUG

        } catch (Exception e) {
            System.err.println("ERROR CRÍTICO al inicializar HttpClient en TruenasConnection: " + e.getMessage()); // DEBUG
            e.printStackTrace();
            throw new RuntimeException("No se pudo inicializar HttpClient para la API", e);
        }
    }

    public boolean createUserFromJson(String jsonPayload) throws IOException, InterruptedException {
        String url = buildUrl(API_ENDPOINT_CREATE_USER);
        System.out.println("DEBUG: TruenasConnection - createUserFromJson (String) llamado para URL: " + url);
        System.out.println("DEBUG: TruenasConnection - createUserFromJson (String) JSON Payload: " + jsonPayload);

        return sendCreateUserRequest(url, jsonPayload); // Ahora devuelve si fue exitoso
    }

    private boolean sendCreateUserRequest(String url, String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = sendRequest(request);

        if (isSuccess(response)) {
            System.out.println("ok");
            return true;
        } else {
            handleApiError(url, response);
            return false; // Esto es por si no lanza excepción, pero igual fue fallo
        }
    }


    // --- Métodos privados ---
    private String buildUrl(String endpoint) {
        return (USE_HTTPS ? "https://" : "http://") + host + endpoint;
    }

    private HttpRequest buildGetRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        System.out.println("DEBUG: TruenasConnection - Enviando " + request.method() + " request a " + request.uri()); // DEBUG
        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("DEBUG: TruenasConnection - Respuesta recibida. Estatus: " + response.statusCode()); // DEBUG
            String bodyPreview = response.body() != null ? (response.body().length() > 500 ? response.body().substring(0, 500) + "..." : response.body()) : "null";
            System.out.println("DEBUG: TruenasConnection - Vista previa del cuerpo de la respuesta: " + bodyPreview); // DEBUG
            return response;
        } catch (IOException | InterruptedException e) {
            System.err.println("ERROR: TruenasConnection - Excepción durante la solicitud HTTP a " + request.uri() + ": " + e.getMessage()); // DEBUG
            throw e; // Relanzar la excepción para que el llamador la maneje
        }
    }

    private boolean isSuccess(HttpResponse<?> response) {
        return response != null && response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private void handleApiError(String url, HttpResponse<String> response) throws IOException {
        String errorDetails = "Error de API para la solicitud a '" + url +
                "': Status=" + (response != null ? response.statusCode() : "N/A") +
                ", Cuerpo=" + (response != null ? response.body() : "Sin cuerpo de respuesta");
        System.err.println("ERROR: TruenasConnection - " + errorDetails); // Log del error
        throw new IOException(errorDetails);
    }
}
