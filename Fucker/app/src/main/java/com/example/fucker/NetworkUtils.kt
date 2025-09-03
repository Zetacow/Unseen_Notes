package com.example.fucker

// Checks if two IPs are on the same /24 subnet
fun isSameSubnet(ip1: String, ip2: String): Boolean {
    val subnet1 = ip1.substringBeforeLast('.')
    val subnet2 = ip2.substringBeforeLast('.')
    return subnet1 == subnet2
}
