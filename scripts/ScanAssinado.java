// Faz uma tentativa de reconhecimento assinada, como o quiosque /scan faz,
// para testar a API sem o front-end (docs/seguranca.md, seção 1.1).
//
// Uso (JDK 25):
//   java scripts/ScanAssinado.java <url-base> <terminalId> <chave> [--pin 1234] foto1.jpg [foto2.jpg ...]
//
// O id e a chave vêm de POST /api/admin/terminais (a chave aparece uma vez só).
// Mande vários frames de uma piscada (olho aberto, fechado, aberto) para
// passar no liveness, ou suba a API com LIVENESS_EXIGIR_PISCADA=false.

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

void main(String[] args) throws Exception {
    if (args.length < 4) {
        IO.println("uso: java scripts/ScanAssinado.java <url-base> <terminalId> <chave> [--pin 1234] foto1.jpg [foto2.jpg ...]");
        System.exit(2);
    }
    String base = args[0];
    String terminalId = args[1];
    String chave = args[2];
    String pin = null;
    List<byte[]> frames = new ArrayList<>();
    for (int i = 3; i < args.length; i++) {
        if (args[i].equals("--pin")) {
            pin = args[++i];
        } else {
            frames.add(Files.readAllBytes(Path.of(args[i])));
        }
    }

    HttpClient http = HttpClient.newHttpClient();
    String desafio = http.send(HttpRequest.newBuilder(URI.create(base + "/api/recognition/desafio"))
            .header("X-Terminal-Id", terminalId).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    Matcher m = Pattern.compile("\"nonce\"\\s*:\\s*\"([^\"]+)\"").matcher(desafio);
    if (!m.find()) {
        IO.println("Desafio recusado: " + desafio);
        System.exit(1);
    }
    String nonce = m.group(1);
    String timestamp = String.valueOf(System.currentTimeMillis());

    // Mensagem canônica — a mesma de TerminalAutenticacaoService e de lib/terminal.ts no front.
    List<String> hashes = new ArrayList<>();
    for (byte[] frame : frames) {
        hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(frame)));
    }
    String mensagem = String.join("\n", "SIAB-SCAN-v1", terminalId, nonce, timestamp,
            String.join(",", hashes), pin == null ? "" : pin);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(Base64.getDecoder().decode(chave), "HmacSHA256"));
    String assinatura = Base64.getEncoder().encodeToString(mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8)));

    String fronteira = "----siab" + UUID.randomUUID();
    ByteArrayOutputStream corpo = new ByteArrayOutputStream();
    for (int i = 0; i < frames.size(); i++) {
        corpo.write(("--" + fronteira + "\r\nContent-Disposition: form-data; name=\"imagens\"; filename=\"f" + i
                + ".jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        corpo.write(frames.get(i));
        corpo.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    if (pin != null) {
        corpo.write(("--" + fronteira + "\r\nContent-Disposition: form-data; name=\"pin\"\r\n\r\n" + pin + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }
    corpo.write(("--" + fronteira + "--\r\n").getBytes(StandardCharsets.UTF_8));

    HttpResponse<String> resposta = http.send(HttpRequest.newBuilder(URI.create(base + "/api/recognition/scan"))
            .header("Content-Type", "multipart/form-data; boundary=" + fronteira)
            .header("X-Terminal-Id", terminalId)
            .header("X-Desafio", nonce)
            .header("X-Timestamp", timestamp)
            .header("X-Assinatura", assinatura)
            .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    IO.println(resposta.statusCode() + " " + resposta.body());
}
