import java.util.*;
public class TestParse3 {
    public static Object parseInput(String input, String type) {
        input = input.trim();
        if (type.equals("int[]")) {
            String clean = input.replaceAll("\\[|\\]", "").trim();
            if (clean.isEmpty()) return new int[0];
            String[] parts = clean.split("[,\\s]+");
            int[] arr = new int[parts.length];
            for(int i=0; i<parts.length; i++) arr[i] = Integer.parseInt(parts[i]);
            return arr;
        }
        return input;
    }
    public static void main(String[] args) {
        int[] r1 = (int[]) parseInput("[4, 5, 2, 25]", "int[]");
        int[] r2 = (int[]) parseInput("[4 5 2 25]", "int[]");
        System.out.println(Arrays.toString(r1));
        System.out.println(Arrays.toString(r2));
    }
}
