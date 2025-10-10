package org.cli.server;

public class Product {
    // Atributos
    private int id;
    private String name;
    private String type;
    private String brand;
    private String info;
    private double price;
    private int stock;

    private String imageUrl;

    // Constructores
    public Product() {}
    public Product(int id, String name, String type, String brand, String info, double price, int stock, String imageUrl) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.brand = brand;
        this.info = info;
        this.price = price;
        this.stock = stock;
        this.imageUrl = imageUrl;
    }

    // Funciones
    public void changeStock(int qty) {
        this.stock += qty;
    }
    @Override
    public String toString() {
        return String.format(
            "\n#%s %s (%s) \n%s - %s \n$%.2f, x%d disponibles\n",
            id, name, type, brand, info, price, stock
        );
    }

    // Getters
    public int getId() { return id; }
    public String getName() { return name; }
    public String getType() {return type; }
    public String getBrand() {return brand; }
    public String getInfo() { return info; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public String getImageUrl() { return imageUrl; }
}