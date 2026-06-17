package br.com.meli.order.application;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class CouponCombinationLoader {

    private CouponCombinationLoader() {}

    public static Stream<CouponScenario> load(String resourcePath) throws Exception {
        URL resource = CouponCombinationLoader.class.getClassLoader().getResource(resourcePath);
        if (resource == null) {
            throw new IllegalArgumentException("Arquivo de cenários não encontrado: " + resourcePath);
        }
        List<String> lines = Files.readAllLines(Path.of(resource.toURI()));
        return lines.stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .map(CouponScenario::parse);
    }
}
