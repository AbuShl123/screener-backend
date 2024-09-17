package dev.abu.screener_backend;

public class Main {
    public static void main(String[] args) {

        int max = 0;

        for (int i = 0; i <= 100; i++) {
            if (i % 2 == 0) {
                max += i;
            }
        }

        System.out.println(max);
    }
}
