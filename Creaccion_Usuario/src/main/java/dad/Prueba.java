package dad;

import java.io.FileInputStream; // Volvemos a necesitarlo
import java.io.IOException;
import java.io.InputStream; // Volvemos a necesitarlo
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.security.KeyStore; // Volvemos a necesitarlo
import java.security.SecureRandom; // Volvemos a necesitarlo
import java.security.cert.CertificateFactory; // Volvemos a necesitarlo
import java.security.cert.X509Certificate; // Volvemos a necesitarlo
import java.time.Duration;
import javax.net.ssl.SSLContext; // Volvemos a necesitarlo
import javax.net.ssl.TrustManagerFactory; // Volvemos a necesitarlo
// Ya NO necesitamos KeyManagerFactory ni UnrecoverableKeyException

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.security.cert.CertificateException;
import java.io.FileNotFoundException;

public class Prueba {

    // --- Configuración ---
    private static final String TRUENAS_HOST = "192.168.1.252";
    private static final String API_ENDPOINT_CREATE_USER = "/api/v2.0/user";
    private static final String TRUENAS_API_KEY = "3-q35B7QbljPTfeNC5shQWlFyVa8Lc13J0nC4N25ePM7s71QAZHNSrgR4CuzVd7vs5";
    private static final boolean USE_HTTPS = true;

    // *** SOLO NECESITAMOS LA RUTA A LA CA ***
    private static final String CA_CERT_PATH = "C:\\Users\\darma\\Downloads\\Autoridad_Certificados.crt";
    // ---------------------------------------------

    public static void main(String[] args) {
        System.out.println("Iniciando prueba de API a TrueNAS usando API Key y TrustStore personalizado...");

        if (!USE_HTTPS) {
            System.err.println("Error: Se requiere HTTPS para esta configuración.");
            return;
        }
        if (TRUENAS_API_KEY == null || TRUENAS_API_KEY.isBlank() || TRUENAS_API_KEY.contains("TU_API_KEY")) {
            System.err.println("Error: Debes configurar la constante TRUENAS_API_KEY con tu clave de API real.");
            return;
        }
        if (CA_CERT_PATH == null || CA_CERT_PATH.contains("/ruta/completa/a/tu") || CA_CERT_PATH.isBlank()) {
            System.err.println("Error: Debes configurar la constante CA_CERT_PATH con la ruta al certificado de tu CA");
            return;
        }

        try {
            // --- Preparación del SSLContext SOLO con TrustStore ---

            // 1. Cargar el TrustStore (contiene la CA en la que confiamos para verificar el servidor)
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null); // Inicializar TrustStore vacío

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate caCertificate;
            try (InputStream caCertStream = new FileInputStream(CA_CERT_PATH)) {
                caCertificate = (X509Certificate) certificateFactory.generateCertificate(caCertStream);
            }
            trustStore.setCertificateEntry("truenas-ca", caCertificate); // Alias para la CA

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            System.out.println("TrustStore personalizado cargado exitosamente desde: " + CA_CERT_PATH);

            // 2. Crear el SSLContext personalizado SOLO con TrustManagers
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            // Inicializar SOLO con TrustManagers (el primer argumento es null porque no usamos KeyManager)
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
            System.out.println("SSLContext creado y configurado con TrustStore personalizado.");

            // --- Fin Preparación SSLContext ---


            // 3. Configurar el cliente HTTP para usar el SSLContext personalizado
            HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(10))
                    .sslContext(sslContext) // ¡¡Usar nuestro SSLContext!!
                    .build();
            System.out.println("HttpClient configurado con SSLContext personalizado.");


            // --- Definir el cuerpo JSON directamente como String ---
            String jsonBody = """
            {
                "username": "java",
                "full_name": "Usuario Java JSON Directo con Trust",
                "password": "PasswordTrust789!",
                "password_disabled": false,
                "group_create": true,
                "smb": false
            }
            """;
            System.out.println("JSON Body a enviar: " + jsonBody);


            // --- Crear Solicitud HTTP POST ---
            String truenasUrl = (USE_HTTPS ? "https://" : "http://") + TRUENAS_HOST + API_ENDPOINT_CREATE_USER;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(truenasUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + TRUENAS_API_KEY)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(BodyPublishers.ofString(jsonBody))
                    .build();

            // --- Enviar Solicitud ---
            System.out.println("Enviando solicitud POST a: " + truenasUrl);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // --- Procesar Respuesta ---
            int statusCode = response.statusCode();
            String responseBody = response.body();

            System.out.println("--------------------------------------------------");
            System.out.println("Código de estado recibido: " + statusCode);
            System.out.println("--------------------------------------------------");
            System.out.println("Cuerpo de la respuesta:");
            System.out.println(responseBody);
            System.out.println("--------------------------------------------------");

            if (statusCode >= 200 && statusCode < 300) {
                System.out.println("¡Usuario creado exitosamente usando API Key y TrustStore!");
            } else {
                System.err.println("Error al crear usuario. Código: " + statusCode);
                System.err.println("El cuerpo de la respuesta puede contener más detalles del error.");
                // ... (mensajes de error como antes) ...
            }

            // Manejo de excepciones ajustado (ya no necesitamos UnrecoverableKeyException)
        } catch (FileNotFoundException e) {
            System.err.println("Error CRÍTICO: No se encontró el archivo de certificado de la CA: " + e.getMessage());
            System.err.println("Verifica la ruta en la constante CA_CERT_PATH ('" + CA_CERT_PATH + "').");
            e.printStackTrace();
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | KeyManagementException e) {
            System.err.println("Error CRÍTICO configurando el TrustStore o SSLContext: " + e.getMessage());
            System.err.println("Asegúrate de que el archivo de certificado de la CA no esté corrupto.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Error durante la comunicación HTTP: " + e.getMessage());
            // Si AÚN obtienes SSLHandshakeException, podría ser que el CERTIFICADO DEL SERVIDOR
            // NO esté firmado por la CA que pusiste en CA_CERT_PATH.
            if (e instanceof javax.net.ssl.SSLHandshakeException) {
                System.err.println("Detalle SSLHandshakeException: Verifica que el certificado HTTPS que usa TrueNAS esté realmente firmado por '" + CA_CERT_PATH + "'");
            }
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("La operación fue interrumpida: " + e.getMessage());
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error inesperado general: " + e.getMessage());
            e.printStackTrace();
        }
    }
}