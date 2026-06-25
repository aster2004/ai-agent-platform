package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 计算器服务层
 * 提供基础数学运算逻辑
 */
@Service
public class AppService {

    private static final Logger log = LoggerFactory.getLogger(AppService.class);

    /**
     * 支持的运算符集合
     */
    private static final Set<String> SUPPORTED_OPERATORS = Set.of("+", "-", "*", "/");

    /**
     * 判断运算符是否受支持
     *
     * @param operator 运算符
     * @return true 如果支持
     */
    public boolean isSupportedOperator(String operator) {
        return SUPPORTED_OPERATORS.contains(operator);
    }

    /**
     * 执行数学运算
     *
     * @param number1 第一个操作数
     * @param number2 第二个操作数
     * @param operator 运算符（+、-、*、/）
     * @return 计算结果
     * @throws IllegalArgumentException 如果运算符不支持
     * @throws ArithmeticException 如果除数为0
     */
    public double calculate(double number1, double number2, String operator) {
        log.debug("执行计算: {} {} {}", number1, operator, number2);

        double result;
        switch (operator) {
            case "+":
                result = number1 + number2;
                break;
            case "-":
                result = number1 - number2;
                break;
            case "*":
                result = number1 * number2;
                break;
            case "/":
                if (number2 == 0) {
                    throw new ArithmeticException("除数不能为0");
                }
                result = number1 / number2;
                break;
            default:
                throw new IllegalArgumentException("不支持的运算符: " + operator);
        }

        log.debug("计算结果: {}", result);
        return result;
    }
}
