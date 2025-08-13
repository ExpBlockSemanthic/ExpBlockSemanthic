package gr.edu.ihu.expblock;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;

import org.apache.commons.codec.language.Soundex;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.io.File;
import java.text.Normalizer;
import java.util.*;

public class NomeSimilaridade {

    private static Word2Vec vec;
    private static Soundex soundex = new Soundex();

    public static void main(String[] args) throws Exception {
        // Carrega o modelo apenas uma vez
        File modelo = new File("C:\\Users\\Pichau\\Desktop\\Elife\\Elife\\ProjetoBD\\GoogleNews-vectors-negative300.bin");
        System.out.println("Carregando modelo pré-treinado...");
        vec = WordVectorSerializer.readWord2VecModel(modelo);
        System.out.println("Modelo carregado!");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Digite o primeiro nome: ");
            String nome1 = scanner.nextLine();

            System.out.print("Digite o segundo nome: ");
            String nome2 = scanner.nextLine();

            Map<String, Double> resultados = calcularSimilaridades(nome1, nome2);

            resultados.forEach((tipo, valor) -> {
                System.out.printf("%s: %.2f%%\n", tipo, valor);
            });
        }
    }

    public static Map<String, Double> calcularSimilaridades(String nome1, String nome2) {
        Map<String, Double> resultado = new LinkedHashMap<>();

        String nome1Corrigido = corrigirSeNecessario(vec, soundex, nome1.toLowerCase());
        String nome2Corrigido = corrigirSeNecessario(vec, soundex, nome2.toLowerCase());

        // Se não for possível usar Word2Vec, aplica apenas fallbacks
        if (nome1Corrigido == null || nome2Corrigido == null) {
            resultado.put("Levenshtein", calcularLevenshteinComposto(nome1, nome2));
            resultado.put("Fonética (Soundex)", calcularSoundexSimilarity(nome1, nome2));
            resultado.put("CharEmbedding", calcularCharEmbeddingSimilarity(nome1, nome2));
            return resultado;
        }

        // Similaridade semântica com Word2Vec
        double similaridadeSemantica = vec.similarity(nome1Corrigido, nome2Corrigido);
        resultado.put("Semântica (Word2Vec)", similaridadeSemantica);

        // Também calcula as outras para ter mais métricas
        resultado.put("Levenshtein", calcularLevenshteinComposto(nome1, nome2));
        resultado.put("Fonética (Soundex)", calcularSoundexSimilarity(nome1, nome2));
        resultado.put("CharEmbedding", calcularCharEmbeddingSimilarity(nome1, nome2));

        return resultado;
    }

    private static String corrigirSeNecessario(Word2Vec vec, Soundex soundex, String palavra) {
        if (vec.hasWord(palavra)) {
            return palavra;
        }
        palavra = limparTexto(palavra);
        String codigo = soundex.encode(palavra);
        Collection<String> similares = vec.wordsNearest(codigo, 1);

        if (similares.isEmpty()) {
            return null;
        }
        return similares.iterator().next();
    }

    public static String limparTexto(String texto) {
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        normalizado = normalizado.replaceAll("[^\\p{ASCII}]", "");
        normalizado = normalizado.replaceAll("[^a-zA-Z]", " ");
        return normalizado.toLowerCase().trim();
    }

    public static double calcularLevenshteinComposto(String nome1, String nome2) {
        String[] partes1 = limparTexto(nome1).split("\\s+");
        String[] partes2 = limparTexto(nome2).split("\\s+");

        LevenshteinDistance levenshtein = new LevenshteinDistance();
        List<Double> similaridades = new ArrayList<>();
        int maxPartes = Math.max(partes1.length, partes2.length);

        for (int i = 0; i < maxPartes; i++) {
            String p1 = i < partes1.length ? partes1[i] : "";
            String p2 = i < partes2.length ? partes2[i] : "";

            int dist = levenshtein.apply(p1, p2);
            int maxLen = Math.max(p1.length(), p2.length());

            double sim = maxLen == 0 ? 0 : 1.0 - ((double) dist / maxLen);
            similaridades.add(sim);
        }
        return similaridades.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public static double calcularSoundexSimilarity(String nome1, String nome2) {
        return soundex.encode(limparTexto(nome1)).equals(soundex.encode(limparTexto(nome2))) ? 1.0 : 0.0;
    }

    public static double calcularCharEmbeddingSimilarity(String nome1, String nome2) {
        Set<Character> chars1 = new HashSet<>();
        Set<Character> chars2 = new HashSet<>();

        for (char c : limparTexto(nome1).toCharArray()) chars1.add(c);
        for (char c : limparTexto(nome2).toCharArray()) chars2.add(c);

        Set<Character> intersecao = new HashSet<>(chars1);
        intersecao.retainAll(chars2);

        Set<Character> uniao = new HashSet<>(chars1);
        uniao.addAll(chars2);

        return uniao.isEmpty() ? 0.0 : (double) intersecao.size() / uniao.size();
    }
}
