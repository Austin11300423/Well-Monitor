package com.cd.well_monitor.utils;

public class PredictAlgorithmUtil {

    public static double[] predictGM11(double[] historyData, int predictDays) {
        int n = historyData.length;

        // 【核心修复】：寻找真实基准产量，无视模拟器注入的 0 值异常
        double baseValue = 0;
        int validCount = 0;
        // 往前找最近的 5 天，把正常的产量平均一下作为基准
        for (int i = n - 1; i >= Math.max(0, n - 5); i--) {
            if (historyData[i] > 0.5) {
                baseValue += historyData[i];
                validCount++;
            }
        }
        baseValue = validCount > 0 ? (baseValue / validCount) : (n > 0 ? historyData[n - 1] : 10.0);
        if (baseValue <= 0.1) baseValue = 10.0; // 绝对兜底，防止归零

        if (n < 4) {
            return generateFallback(baseValue, predictDays, 0.002);
        }

        // 1. 累加生成序列 (1-AGO)
        double[] x1 = new double[n];
        x1[0] = historyData[0];
        for (int i = 1; i < n; i++) {
            x1[i] = x1[i - 1] + historyData[i];
        }

        // 2. 紧邻均值生成序列 Z
        double[] z = new double[n];
        for (int i = 1; i < n; i++) {
            z[i] = 0.5 * (x1[i] + x1[i - 1]);
        }

        // 3. 最小二乘法求解 (使用更严谨的矩阵变量名)
        double B_T_B_00 = 0, B_T_B_01 = 0, B_T_B_11 = n - 1;
        double B_T_Y_0 = 0, B_T_Y_1 = 0;

        for (int i = 1; i < n; i++) {
            B_T_B_00 += z[i] * z[i];
            B_T_B_01 += -z[i];
            B_T_Y_0 += -z[i] * historyData[i];
            B_T_Y_1 += historyData[i];
        }

        double det = B_T_B_00 * B_T_B_11 - B_T_B_01 * B_T_B_01;
        if (Math.abs(det) < 1e-8) {
            return generateFallback(baseValue, predictDays, 0.002);
        }

        double a = (B_T_B_11 * B_T_Y_0 - B_T_B_01 * B_T_Y_1) / det;
        double b = (-B_T_B_01 * B_T_Y_0 + B_T_B_00 * B_T_Y_1) / det;

        // 4. 预测与还原
        double[] result = new double[predictDays];
        double c = historyData[0] - b / a;

        for (int i = 0; i < predictDays; i++) {
            int k = n + i;
            double x1_k = c * Math.exp(-a * (k - 1)) + b / a;
            double x1_k1 = c * Math.exp(-a * k) + b / a;
            double val = x1_k1 - x1_k;

            // 熔断机制：如果发散、变成负数、或者剧烈暴涨，使用安全基准降级
            if (Double.isNaN(val) || val <= 0.1 || val > baseValue * 1.5 || a < -0.05 || a > 0.5) {
                val = baseValue * Math.exp(-0.003 * (i + 1));
            }
            result[i] = val;
        }
        return result;
    }

    private static double[] generateFallback(double value, int days, double decayRate) {
        double[] fallback = new double[days];
        for (int i = 0; i < days; i++) {
            fallback[i] = value * Math.exp(-decayRate * (i + 1));
        }
        return fallback;
    }
}