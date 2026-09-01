package br.com.faitec.falacidade.implementation.service.tools;

import br.com.faitec.falacidade.port.service.tools.ResourceFileService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Service
public class ResourceFileServiceImpl implements ResourceFileService {

    @Override
    public String read(String resourcePath) throws IOException {
        ClassLoader classLoader = ResourceFileServiceImpl.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(resourcePath);

        if (inputStream == null) {
            throw new RuntimeException("Arquivo de recurso não encontrado: " + resourcePath);
        }

        // BUGFIX: era String content = ""; content += line; dentro de loop → O(n²)
        // Corrigido para StringBuilder
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
