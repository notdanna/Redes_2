package org.cli.server;

import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;

public class Items {
    private final Map<Integer, Product> items = new HashMap<>();

    /* CONSTRUCTORES */
    // Cargar inventario por defecto si no hay un JSON disponible
    public Items() {}
    public Items(String path) throws Exception {
        // Intenta cargar archivo JSON
        System.out.println(path);
        try(InputStream in = Items.class.getResourceAsStream(path)) {
            if(in != null) {
                System.out.println("Inventario cargado desde: resources:" + path);
                Items loaded = fromJson(in);
                this.items.putAll(loaded.items);
                return;
            }
            System.err.println("Archivo " + path + " JSON no enconrado");
        } catch(Exception e) {
            System.err.println("No fue posible cargar el archivo JSON: " + e.getMessage());
        }

        // Carga inventario por defecto
        System.out.println("Inventario por defecto cargado");
        this.items.putAll(defaultItems().items);
    }

    /* FUNCIONES */
    // Carga el inventario con un JSON
    public static Items fromJson(InputStream in) throws Exception {
        ObjectMapper mapper = new ObjectMapper(); 
        List<Product> inventory = mapper.readValue(in, new TypeReference<List<Product>>() {});
        Items it = new Items(); 

        for (Product p : inventory) {
            if (p == null) continue; 
            
            // Validar que los campos sean válidos
            if (p.getId() <= 0)
                throw new IllegalArgumentException("Invalid or missing product ID in JSON");
            if (p.getName() == null || p.getName().isBlank())
                throw new IllegalArgumentException("Missing product name in JSON (id=" + p.getId() + ")");
            if (p.getBrand() == null || p.getBrand().isBlank())
                throw new IllegalArgumentException("Missing product brand in JSON (id=" + p.getId() + ")");
            if (p.getType() == null || p.getType().isBlank())
                throw new IllegalArgumentException("Missing product type in JSON (id=" + p.getId() + ")");
            if (p.getInfo() == null)
                throw new IllegalArgumentException("Missing product info in JSON (id=" + p.getId() + ")");
            if (p.getPrice() < 0)
                throw new IllegalArgumentException("Negative price for product in JSON (id=" + p.getId() + ")");
            if (p.getStock() < 0)
                throw new IllegalArgumentException("Negative stock for product in JSON (id=" + p.getId() + ")");
            
            // Detecta IDs duplicados
            if (it.items.putIfAbsent(p.getId(), p) != null)
                throw new IllegalArgumentException("ID de producto duplicado en JSON: " + p.getId());
        }
        return it;
    }

    // Carga el inventario por defecto
    public static Items defaultItems() {
        Items it = new Items();
        it.add(101, new Product(101, "Cactus Espiral", "Suculenta", "Vivero Verde", "Cactus decorativo, fácil de cuidar", 12.5, 20));
        it.add(102, new Product(102, "Orquídea Phalaenopsis", "Interior", "Flores del Valle", "Orquídea elegante de interior", 25.0, 15));
        it.add(103, new Product(103, "Bonsái Ficus", "Decorativa", "Jardín Zen", "Mini árbol para interiores", 45.0, 10));
        it.add(104, new Product(104, "Helecho Boston", "Interior", "EcoPlant", "Helecho frondoso de fácil mantenimiento", 18.0, 25));
        it.add(105, new Product(105, "Aloe Vera", "Medicinal", "GreenHouse Co.", "Planta suculenta con propiedades medicinales", 10.0, 30));
        it.add(106, new Product(106, "Lavanda", "Aromática", "Aromas Naturales", "Planta aromática ideal para interiores y jardines", 15.0, 12));
        return it;
    }

    // Agrega o actualiza valores al inventario
    public void add(int id, Product p) {
        items.put(id, p);
    }

    // Encuentra un producto por ID
    public Product findById(int id) {
        return items.get(id);
    }

    // Encuentra un producto por nombre, marca o ID
    public Map<Integer, Product> find(String name) {
        String newName = name.toLowerCase();
        Map<Integer, Product> aux = new LinkedHashMap<>();
        boolean isID = name.matches("\\d+");     // Validamos si es un número

        for (Map.Entry<Integer, Product> entry : items.entrySet()) {
            Product p = entry.getValue();
            if(p.getName().toLowerCase().contains(newName)  || 
               p.getBrand().toLowerCase().contains(newName) ||
               (isID && Integer.parseInt(name) == entry.getKey()))
                aux.put(entry.getKey(), p);
        }

        return aux;
    }

    // Encuentra un producto por tipo
    public Map<Integer, Product> findByType(String type) {
        Map<Integer, Product> aux = new LinkedHashMap<>();
        for (Map.Entry<Integer, Product> entry : items.entrySet()) {
            Product p = entry.getValue();
            if(p.getType().equalsIgnoreCase(type))
                aux.put(entry.getKey(), p);
        }

        return aux;
    }

    // Añade productos al carrito y ajusta existencias
    public int addToCart(int id, int qty) {
        if(items.get(id) == null) return 0;
        if(items.get(id).getStock() - qty < 0) return -1;
        items.get(id).changeStock(-qty);
        return 1;
    }

    // Regresa productos del carrito al inventario y ajusta existencias
    public boolean returnFromCart(int id, int qty) {
        if(items.get(id) == null) return false;
        items.get(id).changeStock(Math.abs(qty));
        return true;
    }

    // Getters
    public Map<Integer, Product> getItems() {return items; }
}