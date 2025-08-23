// SimilarityService.java
package gr.edu.ihu.expblock;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import gr.edu.ihu.expblock.ExpBlock.ExperimentConfig;

import org.apache.commons.text.similarity.LevenshteinDistance;

import java.io.File;
import java.text.Normalizer;
import java.util.*;

public class SimilarityService {

    private static WordVectors vec;
    private static boolean isInitialized = false;

    // Cache LRU de embeddings para acelerar comparações e limitar memória
    private static final int CACHE_MAX = 100_000;
    private static final Map<String, double[]> embeddingCache = new LinkedHashMap<String, double[]>(CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, double[]> eldest) {
            return size() > CACHE_MAX;
        }
    };

    // Classe de retorno para agrupar os resultados
    public static class SimilarityScores {
        public double semanticScore;
        public double levenshteinScore;
        public double charEmbeddingScore;
    }

    public static void initialize(String modelPath) {
        if (isInitialized) return;
        File modelo = new File(modelPath);
        try {
            System.out.println("Carregando modelo (.vec/.bin/.zip) com loadStaticModel...");
            // Carrega como modelo estático (mais leve p/ lookup). Aceita .vec também.
            vec = WordVectorSerializer.loadStaticModel(modelo);
            isInitialized = true;
            System.out.println("Modelo carregado (static).");
        } catch (Exception e1) {
            System.err.println("Falha no loadStaticModel: " + e1.getMessage());
            try {
                System.out.println("Tentando readWord2VecModel (auto-detect de formato)...");
                // Fallback: também lê formato texto (.vec) além de binário
                vec = WordVectorSerializer.readWord2VecModel(modelo);
                isInitialized = true;
                System.out.println("Modelo carregado com readWord2VecModel.");
            } catch (Exception e2) {
                System.err.println("Falha ao carregar o modelo Word2Vec: " + e2.getMessage());
                vec = null;
            }
        }
    }

    /** Calcula os scores de similaridade entre duas strings */
    public static SimilarityScores getScores(String str1, String str2, ExperimentConfig config) {
        SimilarityScores scores = new SimilarityScores();

        // --- Similaridade Semântica ---
        if (config.semanticSimilarityWeight > 0.0 && isInitialized && vec != null) {
            double[] vec1 = sentenceVector(str1);
            double[] vec2 = sentenceVector(str2);
            scores.semanticScore = (vec1 != null && vec2 != null) ? cosineSimilarity(vec1, vec2) : 0.0;
        } else {
            scores.semanticScore = 0.0;
        }

        // --- Similaridades Sintáticas ---
        scores.levenshteinScore = calcularLevenshteinComposto(str1, str2);
        scores.charEmbeddingScore = calcularCharEmbeddingSimilarity(str1, str2);

        return scores;
    }

    // ---------- SEMÂNTICA COM EMBEDDINGS ----------

    private static double[] getVectorCached(String token) {
        double[] cached = embeddingCache.get(token);
        if (cached != null) return cached;
        if (vec != null && vec.hasWord(token)) {
            double[] v = vec.getWordVector(token);
            if (v != null) embeddingCache.put(token, v);
            return v;
        }
        return null;
    }

    private static double[] sentenceVector(String frase) {
        String[] tokens = limparTexto(frase).split(" ");
        List<double[]> vectors = new ArrayList<>();
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            double[] v = getVectorCached(t);
            if (v != null) vectors.add(v);
        }
        if (vectors.isEmpty()) return null;

        int dim = vectors.get(0).length;
        double[] mean = new double[dim];
        for (double[] v : vectors) {
            for (int i = 0; i < dim; i++) mean[i] += v[i];
        }
        for (int i = 0; i < dim; i++) mean[i] /= vectors.size();
        return mean;
    }

    private static double cosineSimilarity(double[] v1, double[] v2) {
        double dot = 0.0, n1 = 0.0, n2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            n1 += v1[i] * v1[i];
            n2 += v2[i] * v2[i];
        }
        return (n1 == 0 || n2 == 0) ? 0.0 : dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }

    // ---------- SINTÁTICA ----------

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
        List<Double> sims = new ArrayList<>();
        int maxPartes = Math.max(partes1.length, partes2.length);

        for (int i = 0; i < maxPartes; i++) {
            String p1 = i < partes1.length ? partes1[i] : "";
            String p2 = i < partes2.length ? partes2[i] : "";
            if (p1.isEmpty() && p2.isEmpty()) continue;

            int dist = levenshtein.apply(p1, p2);
            int maxLen = Math.max(p1.length(), p2.length());
            double sim = maxLen == 0 ? 1.0 : 1.0 - ((double) dist / maxLen);
            sims.add(sim);
        }
        return sims.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double calcularCharEmbeddingSimilarity(String nome1, String nome2) {
        Set<Character> chars1 = new HashSet<>();
        Set<Character> chars2 = new HashSet<>();
        for (char c : limparTexto(nome1).toCharArray()) chars1.add(c);
        for (char c : limparTexto(nome2).toCharArray()) chars2.add(c);

        Set<Character> inter = new HashSet<>(chars1);
        inter.retainAll(chars2);
        Set<Character> uniao = new HashSet<>(chars1);
        uniao.addAll(chars2);

        return uniao.isEmpty() ? 1.0 : (double) inter.size() / uniao.size();
    }
}
