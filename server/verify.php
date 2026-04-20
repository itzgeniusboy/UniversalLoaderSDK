<?php
header('Content-Type: application/json');

/**
 * OneCore SDK Engine Production Verification API 
 * Uses customers.json from admin/data directory
 */

$json_path = '../admin/data/customers.json';
$customers = [];

if (file_exists($json_path)) {
    $json_content = file_get_contents($json_path);
    $customers = json_decode($json_content, true);
}

$key_to_find = isset($_GET['key']) ? $_GET['key'] : '';
$found_customer = null;

foreach ($customers as $cust) {
    if ($cust['key'] === $key_to_find) {
        $found_customer = $cust;
        break;
    }
}

if ($found_customer) {
    if ($found_customer['status'] === 'suspended') {
        echo json_encode([
            "valid" => false,
            "message" => "License suspended. Please contact admin."
        ]);
        exit;
    }

    $expiry = $found_customer['expiry'];
    $is_valid = true;
    $days_left = 999;

    if ($expiry) {
        $expiry_time = strtotime($expiry);
        $now = time();
        $is_valid = ($now <= $expiry_time);
        $days_left = max(0, ceil(($expiry_time - $now) / (60 * 60 * 24)));
    }

    echo json_encode([
        "valid" => $is_valid,
        "expiry" => $expiry,
        "days_left" => $days_left,
        "message" => $is_valid ? "OK" : "License expired."
    ]);
} else {
    echo json_encode([
        "valid" => false,
        "message" => "Invalid license key."
    ]);
}
?>
