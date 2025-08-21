// SimilarityService.java
package gr.edu.ihu.expblock;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;

import gr.edu.ihu.expblock.ExpBlock.ExperimentConfig;

import org.apache.commons.codec.language.Soundex;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.io.File;
import java.text.Normalizer;
import java.util.*;

public class SimilarityService {

    private static Word2Vec vec;
    private static final Soundex soundex = new Soundex();
    private static boolean isInitialized = false;

    // Classe de retorno para agrupar os resultados
    public static class SimilarityScores {
        public double semanticScore;
        public double levenshteinScore;
        public double soundexScore;
        public double charEmbeddingScore;
    }

    public static void initialize(String modelPath) {
        if (isInitialized) {
            return;
        }
        try {
            File modelo = new File(modelPath);
            System.out.println("Carregando modelo pré-treinado Word2Vec...");
            vec = WordVectorSerializer.readWord2VecModel(modelo);
            System.out.println("Modelo carregado com sucesso!");
            isInitialized = true;
        } catch (Exception e) {
            System.err.println("Falha ao carregar o modelo Word2Vec: " + e.getMessage());
            vec = null;
        }
    }

    /**
     * Calculates and returns a collection of similarity scores between two strings.
     * @param str1 The first string.
     * @param str2 The second string.
     * @return A SimilarityScores object containing the scores for each metric.
     */
    public static SimilarityScores getScores(String str1, String str2, ExperimentConfig config) {
        SimilarityScores scores = new SimilarityScores();
    
        // Verificação condicional para pular a similaridade semântica
        if (config.semanticSimilarityWeight > 0.0) {
            if (isInitialized && vec != null) {
                String str1Limpo = limparTexto(str1).replace(" ", "_");
                String str2Limpo = limparTexto(str2).replace(" ", "_");
                String str1Corrigido = corrigirSeNecessario(str1Limpo);
                String str2Corrigido = corrigirSeNecessario(str2Limpo);
                if (str1Corrigido != null && str2Corrigido != null) {
                    scores.semanticScore = Math.max(0, vec.similarity(str1Corrigido, str2Corrigido));
                } else {
                    scores.semanticScore = 0.0;
                }
            } else {
                scores.semanticScore = 0.0;
            }
        } else {
            // Se o peso é 0, o score semântico também será 0, e o cálculo é ignorado.
            scores.semanticScore = 0.0;
        }

        // Similaridades Sintáticas
        scores.levenshteinScore = calcularLevenshteinComposto(str1, str2);
        scores.soundexScore = calcularSoundexSimilarity(str1, str2);
        scores.charEmbeddingScore = calcularCharEmbeddingSimilarity(str1, str2);

        return scores;
    }

    // O método 'getCombinedSimilarity' não é mais necessário, pois a combinação será feita no Block.java

    private static String corrigirSeNecessario(String palavra) {
        if (vec.hasWord(palavra)) {
            return palavra;
        }
        return null;
    }

    private static String limparTexto(String texto) {
        if (texto == null) return "";
        String normalizado = Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD);
        normalizado = normalizado.replaceAll("[^\\p{ASCII}]", "");
        normalizado = normalizado.replaceAll("[^a-z\\s]", "");
        normalizado = normalizado.replaceAll("\\s+", " ");
        return normalizado.trim();
    }

    private static double calcularLevenshteinComposto(String nome1, String nome2) {
        String[] partes1 = limparTexto(nome1).split("\\s+");
        String[] partes2 = limparTexto(nome2).split("\\s+");

        LevenshteinDistance levenshtein = new LevenshteinDistance();
        List<Double> similaridades = new ArrayList<>();
        int maxPartes = Math.max(partes1.length, partes2.length);

        for (int i = 0; i < maxPartes; i++) {
            String p1 = i < partes1.length ? partes1[i] : "";
            String p2 = i < partes2.length ? partes2[i] : "";
            if (p1.isEmpty() && p2.isEmpty()) continue;

            int dist = levenshtein.apply(p1, p2);
            int maxLen = Math.max(p1.length(), p2.length());
            double sim = maxLen == 0 ? 1.0 : 1.0 - ((double) dist / maxLen);
            similaridades.add(sim);
        }
        return similaridades.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double calcularSoundexSimilarity(String nome1, String nome2) {
        return soundex.encode(limparTexto(nome1)).equals(soundex.encode(limparTexto(nome2))) ? 1.0 : 0.0;
    }

    private static double calcularCharEmbeddingSimilarity(String nome1, String nome2) {
        Set<Character> chars1 = new HashSet<>();
        Set<Character> chars2 = new HashSet<>();

        for (char c : limparTexto(nome1).toCharArray()) chars1.add(c);
        for (char c : limparTexto(nome2).toCharArray()) chars2.add(c);

        Set<Character> intersecao = new HashSet<>(chars1);
        intersecao.retainAll(chars2);
        Set<Character> uniao = new HashSet<>(chars1);
        uniao.addAll(chars2);

        return uniao.isEmpty() ? 1.0 : (double) intersecao.size() / uniao.size();
    }
}