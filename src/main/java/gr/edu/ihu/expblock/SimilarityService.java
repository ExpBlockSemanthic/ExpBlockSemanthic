// SimilarityService.java
package gr.edu.ihu.expblock;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.apache.commons.codec.language.Soundex;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.io.File;
import java.text.Normalizer;
import java.util.*;

public class SimilarityService {

    private static Word2Vec vec;
    private static final Soundex soundex = new Soundex();
    private static boolean isInitialized = false;

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
    
    public static double getCombinedSimilarity(String str1, String str2) {
        return getCombinedSimilarity(str1, str2, 0.6, 0.4); 
    }

    /**
     * Calculates a weighted similarity score between two strings.
     * @param str1 The first string.
     * @param str2 The second string.
     * @param semanticWeight The weight for the semantic (Word2Vec) score.
     * @param syntacticWeight The weight for the combined syntactic (Levenshtein + Char) score.
     * @return The final weighted similarity score.
     */
    public static double getCombinedSimilarity(String str1, String str2, double semanticWeight, double syntacticWeight) {
        if (!isInitialized || vec == null) {
            return (calcularLevenshteinComposto(str1, str2) + calcularCharEmbeddingSimilarity(str1, str2)) / 2.0;
        }

        Map<String, Double> scores = calcularSimilaridades(str1, str2);
        
        double semanticScore = scores.getOrDefault("Semântica (Word2Vec)", 0.0);
        
        double levenshteinScore = scores.getOrDefault("Levenshtein", 0.0);
        double charEmbeddingScore = scores.getOrDefault("CharEmbedding", 0.0);
        double syntacticScore = (levenshteinScore + charEmbeddingScore) / 2.0;

        if (semanticScore > 0.85) {
            return semanticScore;
        }
        
        return (semanticScore * semanticWeight) + (syntacticScore * syntacticWeight);
    }

    private static Map<String, Double> calcularSimilaridades(String nome1, String nome2) {
        Map<String, Double> resultado = new LinkedHashMap<>();
        
        if (vec != null) {
            String nome1Limpo = limparTexto(nome1).replace(" ", "_"); 
            String nome2Limpo = limparTexto(nome2).replace(" ", "_");
            String nome1Corrigido = corrigirSeNecessario(nome1Limpo);
            String nome2Corrigido = corrigirSeNecessario(nome2Limpo);
            if (nome1Corrigido != null && nome2Corrigido != null) {
                double similaridadeSemantica = vec.similarity(nome1Corrigido, nome2Corrigido);
                resultado.put("Semântica (Word2Vec)", Math.max(0, similaridadeSemantica)); 
            } else {
                resultado.put("Semântica (Word2Vec)", 0.0);
            }
        }
        
        resultado.put("Levenshtein", calcularLevenshteinComposto(nome1, nome2));
        resultado.put("Fonética (Soundex)", calcularSoundexSimilarity(nome1, nome2));
        resultado.put("CharEmbedding", calcularCharEmbeddingSimilarity(nome1, nome2));

        return resultado;
    }

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