package com.example.controller;

import com.example.service.AppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 计算器服务控制器
 * 提供 RESTful API 接口
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AppController {

    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    @Autowired
    private AppService appService;

    /**
     * 健康检查接口
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        log.debug("健康检查请求");
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    /**
     * 计算接口
     * POST /api/calculate
     *
     * 请求体示例：
     * {
     *   "number1": 10,
     *   "number2": 5,
     *   "operator": "+"
     * }
     */
    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculate(@RequestBody Map<String, Object> request) {
        log.info("收到计算请求: {}", request);

        // 参数校验
        if (!request.containsKey("number1") || !request.containsKey("number2") || !request.containsKey("operator")) {
            log.warn("请求参数缺失: {}", request);
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "缺少必要参数（number1, number2, operator）",
                    "result", null
            ));
        }

        Object num1Obj = request.get("number1");
        Object num2Obj = request.get("number2");
        Object operatorObj = request.get("operator");

        // 校验操作数是否为数字
        if (!(num1Obj instanceof Number) || !(num2Obj instanceof Number)) {
            log.warn("操作数类型错误: number1={}, number2={}", num1Obj, num2Obj);
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "操作数必须为数字类型",
                    "result", null
            ));
        }

        // 校验运算符是否为字符串
        if (!(operatorObj instanceof String)) {
            log.warn("运算符类型错误: operator={}", operatorObj);
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "运算符必须为字符串类型",
                    "result", null
            ));
        }

        double number1 = ((Number) num1Obj).doubleValue();
        double number2 = ((Number) num2Obj).doubleValue();
        String operator = (String) operatorObj;

        // 校验运算符是否支持
        if (!appService.isSupportedOperator(operator)) {
            log.warn("不支持的运算符: {}", operator);
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "不支持的运算符，仅支持 +、-、*、/",
                    "result", null
            ));
        }

        // 除法除零校验
        if ("/".equals(operator) && number2 == 0) {
            log.warn("除数为0: number1={}, number2={}", number1, number2);
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "除数不能为0",
                    "result", null
            ));
        }

        // 执行计算
        try {
            double result = appService.calculate(number1, number2, operator);
            log.info("计算成功: {} {} {} = {}", number1, operator, number2, result);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "success",
                    "result", result
            ));
        } catch (Exception e) {
            log.error("计算异常: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "服务器内部错误: " + e.getMessage(),
                    "result", null
            ));
        }
    }
}
