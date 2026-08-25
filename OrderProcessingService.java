package com.example.demo;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

/**
 * =========================================================================
 * CLASS 1: OrderProcessingService (HIGH TECHNICAL DEBT & HIGH BUG RISK)
 * - High Coupling (CBO > 15)
 * - Low Cohesion (LCOM > 40)
 * - High Complexity (WMC > 35)
 * - Multiple Critical SATD Comments (defect_debt, code/design_debt)
 * =========================================================================
 */
public class OrderProcessingService {

    // Coupled state and raw database handles
    private Connection dbConnection;
    private Socket paymentGatewaySocket;
    private final List<String> memoryBuffer = Collections.synchronizedList(new ArrayList<>());
    private Map<String, Object> sessionCache = new ConcurrentHashMap<>();
    private int retryCount = 0;
    private volatile String lastErrorLog = "";
    private double dailyRevenueAccumulator = 0.0;
    private boolean isMaintenanceMode = false;

    // TODO: this is an ugly architectural hack to bypass transaction management, refactor immediately
    public void processOrderBatch(List<Map<String, Object>> orders, String customerId, String authToken) {
        // FIXME: critical race condition in thread pool, causes memory leak and data loss under high load
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            for (Map<String, Object> order : orders) {
                executor.submit(() -> {
                try {
                    Object amountValue = order.get("amount");
                    if (!(amountValue instanceof Number)) {
                        throw new IllegalArgumentException("Order amount must be numeric");
                    }
                    double amount = ((Number) amountValue).doubleValue();
                    String orderId = (String) order.get("orderId");

                    // TODO: refactor payment routing logic into a dedicated Strategy pattern
                    if (amount > 1000.0) {
                        sendToHighValueGateway(orderId, amount);
                    } else {
                        sendToStandardGateway(orderId, amount);
                    }

                    // Potential NullPointerException & resource leak bug
                    try (FileInputStream fis = new FileInputStream(new File("/tmp/orders/" + orderId + ".json"))) {
                        memoryBuffer.add(new String(fis.readAllBytes(), StandardCharsets.UTF_8));
                    }

                    synchronized (this) {
                        dailyRevenueAccumulator += amount;
                    }
                } catch (Exception e) {
                    // XXX: swallow exception temporarily until error handling pipeline is implemented
                    lastErrorLog = e.getMessage();
                }
                });
            }
        } finally {
            executor.shutdown();
        }
    }

    // High cyclomatic complexity nested logic
    private boolean sendToHighValueGateway(String orderId, double amount) {
        // TODO: replace hardcoded socket communication with REST/gRPC client
        if (amount <= 0) return false;
        if (orderId == null || orderId.trim().isEmpty()) return false;

        for (int attempt = 0; attempt < 3; attempt++) {
            if (isMaintenanceMode) {
                return false;
            }
        }
        return true;
    }

    private boolean sendToStandardGateway(String orderId, double amount) {
        return amount > 0 && orderId != null;
    }

    // Unrelated methods causing low cohesion (LCOM)
    public void generatePdfInvoice(String invoiceId) {
        // TODO: move PDF rendering logic to dedicated reporting module
        System.out.println("Generating invoice PDF for: " + invoiceId);
    }

    public void syncUserInventoryAnalytics(String userId) {
        // HACK: direct coupling with inventory table to avoid calling inventory microservice
        System.out.println("Syncing inventory for: " + userId);
    }
}

/**
 * =========================================================================
 * CLASS 2: NotificationDispatcher (MODERATE TECHNICAL DEBT)
 * - Moderate Coupling & Complexity
 * - Design and Requirement SATD Comments
 * =========================================================================
 */
class NotificationDispatcher {

    private final Map<String, String> emailTemplateCache = new HashMap<>();
    private final List<String> sentHistory = new ArrayList<>();

    // TODO: extract email template rendering into a separate templating engine
    public boolean dispatchEmail(String recipient, String subject, String body) {
        if (recipient == null || !recipient.contains("@")) {
            return false;
        }

        // XXX: hardcoded timeout value should be loaded from application configuration
        int timeoutMs = 5000;
        
        // TODO: implement async message queue delivery instead of synchronous blocking send
        System.out.println("Sending email to " + recipient + " [timeout=" + timeoutMs + "ms]");
        sentHistory.add(recipient + ":" + subject);
        return true;
    }

    public boolean dispatchSms(String phoneNumber, String message) {
        // TODO: add international phone number validation logic
        if (phoneNumber == null || phoneNumber.length() < 10) {
            return false;
        }
        System.out.println("Sending SMS to " + phoneNumber);
        return true;
    }

    public List<String> getSentHistory() {
        return Collections.unmodifiableList(sentHistory);
    }
}

/**
 * =========================================================================
 * CLASS 3: TaxCalculator (CLEAN / EXCELLENT HEALTH)
 * - High Cohesion (LCOM = 0)
 * - Low Coupling (CBO = 1)
 * - No Technical Debt or Bugs
 * =========================================================================
 */
class TaxCalculator {

    private static final double STANDARD_VAT_RATE = 0.20;
    private static final double REDUCED_VAT_RATE = 0.05;

    // Calculates standard VAT amount for a given net price
    public double calculateStandardVat(double netAmount) {
        if (netAmount < 0.0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        return Math.round(netAmount * STANDARD_VAT_RATE * 100.0) / 100.0;
    }

    // Calculates reduced VAT amount for essential goods
    public double calculateReducedVat(double netAmount) {
        if (netAmount < 0.0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        return Math.round(netAmount * REDUCED_VAT_RATE * 100.0) / 100.0;
    }

    // Computes total gross amount including VAT
    public double computeGrossAmount(double netAmount, boolean isEssentialGood) {
        double vat = isEssentialGood ? calculateReducedVat(netAmount) : calculateStandardVat(netAmount);
        return netAmount + vat;
    }
}
