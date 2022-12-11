package ru.itlab.OTH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Other {

    public static void main(String[] args) throws IOException {
        List<String> data = Files.readAllLines(Path.of("Data"));
        System.out.println("BAYES ! Chose the min");
        bayes(data);
        System.out.println("VALDE ! Chose the max");
        valde(data);
        System.out.println("Optimistic ! Chose the max");
        optimistic(data);
        System.out.println("Hurwic ! Chose the max");
        hurwic(data);
        System.out.println("Savidge ! Chose the min");
        savidge(data);
    }

    public static void bayes(List<String> data) {
        for (int i = 0; i < data.size(); i++) {
            Double tmp = 0.0;
            System.out.print("B[" + i + "] = ");
            for (int j = 0; j < data.get(i).split(" ").length; j += 2) {
                tmp += Double.parseDouble(data.get(i).split(" ")[j]) * Double.parseDouble(data.get(i).split(" ")[j + 1]);
            }
            System.out.println(tmp);
        }
    }

    public static void valde(List<String> data) {
        for (int i = 0; i < data.size(); i++) {
            Double tmp = 100000000.0;
            System.out.print("W[" + i + "] = ");
            for (int j = 0; j < data.get(i).split(" ").length; j += 2) {
                if (tmp > Double.parseDouble(data.get(i).split(" ")[j]))
                    tmp = Double.valueOf(data.get(i).split(" ")[j]);
            }
            System.out.println(tmp);
        }
    }

    public static void optimistic(List<String> data) {
        for (int i = 0; i < data.size(); i++) {
            Double tmp = -100000000.0;
            System.out.print("O[" + i + "] = ");
            for (int j = 0; j < data.get(i).split(" ").length; j += 2) {
                if (tmp < Double.parseDouble(data.get(i).split(" ")[j]))
                    tmp = Double.valueOf(data.get(i).split(" ")[j]);
            }
            System.out.println(tmp);
        }
    }

    // k = 0.6
    public static void hurwic(List<String> data) {
        double k = 0.6;
        for (int i = 0; i < data.size(); i++) {
            double tmpMax = -100000000.0;
            double tmpMin = 100000000.0;
            System.out.print("H[" + i + "] = ");
            for (int j = 0; j < data.get(i).split(" ").length; j += 2) {
                if (tmpMax < Double.parseDouble(data.get(i).split(" ")[j]))
                    tmpMax = Double.parseDouble(data.get(i).split(" ")[j]);
                if (tmpMin > Double.parseDouble(data.get(i).split(" ")[j]))
                    tmpMin = Double.parseDouble(data.get(i).split(" ")[j]);
            }
            System.out.println(k * tmpMin + (1 - k) * tmpMax);
        }
    }

    public static void savidge(List<String> data) {
        double[][] matrica = new double[7][4];
        for (int i = 0; i < 8; i += 2) {
            List<Double> tmpList = new ArrayList<>();
            double tmpMax = -100000000.0;
            for (int j = 0; j < data.size(); j++) {
                tmpList.add(Double.parseDouble(data.get(j).split(" ")[i]));
                if (tmpMax < Double.parseDouble(data.get(j).split(" ")[i]))
                    tmpMax = Double.parseDouble(data.get(j).split(" ")[i]);
            }
            for (int j = 0; j < tmpList.size(); j++) {
                matrica[i][j] = tmpMax - tmpList.get(j);
            }
        }
        for (int i = 0; i < 4; i++) {
            double tmpMax = -100000000.0;
            for (int j = 0; j < 8; j += 2) {
                if (tmpMax < matrica[j][i])
                    tmpMax = matrica[j][i];
            }
            System.out.println("S[" + i + "] = " + tmpMax);

        }
    }
}
