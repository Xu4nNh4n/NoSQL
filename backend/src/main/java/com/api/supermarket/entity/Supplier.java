package com.api.supermarket.entity;
import jakarta.persistence.*;
@Entity
@Table(name = "suppliers")
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    public Long supplierId;
    @Column(name = "name", nullable = false, unique = true, length = 150)
    public String supplierName;
    @Column(name = "contact_name", length = 150)
    public String contactName;
    @Column(name = "phone", length = 20)
    public String phone;
    @Column(name = "address", length = 255)
    public String address;
    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;
}
