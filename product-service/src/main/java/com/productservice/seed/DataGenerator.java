package com.productservice.seed;

import com.productservice.entity.Category;
import com.productservice.entity.Inventory;
import com.productservice.entity.Product;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class DataGenerator {

    private static final Map<String, CategoryData> CATEGORY_DATA = new LinkedHashMap<>();

    static {
        CATEGORY_DATA.put("Electronics", new CategoryData(
                "Electronic devices, gadgets, and accessories",
                new String[]{"Samsung", "Sony", "LG", "Anker", "Logitech", "JBL", "Bose", "Philips"},
                new String[]{"Wireless", "Smart", "Portable", "Digital", "Ultra", "Pro", "Mini", "Max"},
                new String[]{"Headphones", "Speaker", "Charger", "Cable", "Adapter", "Monitor", "Keyboard", "Mouse", "Webcam", "Hub"},
                50.0, 500.0, 100, 300
        ));
        CATEGORY_DATA.put("Home Appliances", new CategoryData(
                "Essential home appliances for modern living",
                new String[]{"Dyson", "Shark", "iRobot", "Ninja", "Instant Pot", "Breville", "Keurig", "Nespresso"},
                new String[]{"Smart", "Compact", "Professional", "Deluxe", "Premium", "Essential", "Advanced", "Ultra"},
                new String[]{"Vacuum", "Blender", "Coffee Maker", "Air Fryer", "Toaster", "Iron", "Fan", "Heater", "Humidifier", "Purifier"},
                80.0, 400.0, 50, 200
        ));
        CATEGORY_DATA.put("Kitchen", new CategoryData(
                "Kitchen essentials and cookware",
                new String[]{"KitchenAid", "Cuisinart", "OXO", "Lodge", "Le Creuset", "Pyrex", "Calphalon", "All-Clad"},
                new String[]{"Professional", "Classic", "Premium", "Essential", "Deluxe", "Signature", "Heritage", "Modern"},
                new String[]{"Pan", "Pot", "Knife Set", "Cutting Board", "Mixing Bowl", "Spatula", "Whisk", "Measuring Cup", "Bakeware", "Utensil Set"},
                20.0, 200.0, 100, 400
        ));
        CATEGORY_DATA.put("Grocery", new CategoryData(
                "Fresh groceries and pantry essentials",
                new String[]{"Organic Valley", "Nature's Best", "Green Giant", "Dole", "Del Monte", "Kraft", "Heinz", "Kellogg's"},
                new String[]{"Organic", "Fresh", "Natural", "Premium", "Classic", "Original", "Whole", "Pure"},
                new String[]{"Cereal", "Pasta", "Rice", "Beans", "Sauce", "Snack Pack", "Granola", "Oatmeal", "Crackers", "Chips"},
                2.0, 15.0, 200, 500
        ));
        CATEGORY_DATA.put("Books", new CategoryData(
                "Books across all genres and formats",
                new String[]{"Penguin", "HarperCollins", "Random House", "Simon & Schuster", "Macmillan", "Hachette", "Scholastic", "Oxford"},
                new String[]{"Complete", "Illustrated", "Collector's", "Revised", "Annotated", "Deluxe", "Essential", "Classic"},
                new String[]{"Novel", "Guide", "Handbook", "Encyclopedia", "Anthology", "Biography", "Memoir", "Textbook", "Workbook", "Journal"},
                10.0, 50.0, 150, 400
        ));
        CATEGORY_DATA.put("Sports", new CategoryData(
                "Sports equipment and athletic gear",
                new String[]{"Nike", "Adidas", "Under Armour", "Puma", "Reebok", "Wilson", "Spalding", "Rawlings"},
                new String[]{"Pro", "Elite", "Performance", "Training", "Competition", "Official", "Premium", "Classic"},
                new String[]{"Ball", "Racket", "Gloves", "Pads", "Net", "Goal", "Bat", "Helmet", "Jersey", "Shorts"},
                15.0, 150.0, 80, 250
        ));
        CATEGORY_DATA.put("Fitness", new CategoryData(
                "Fitness equipment and workout accessories",
                new String[]{"Nike", "Adidas", "Bowflex", "NordicTrack", "Peloton", "Fitbit", "Garmin", "Theragun"},
                new String[]{"Pro", "Elite", "Performance", "Training", "Advanced", "Smart", "Compact", "Adjustable"},
                new String[]{"Dumbbell", "Kettlebell", "Resistance Band", "Yoga Mat", "Jump Rope", "Foam Roller", "Weight Bench", "Pull-up Bar", "Ab Wheel", "Tracker"},
                20.0, 300.0, 60, 200
        ));
        CATEGORY_DATA.put("Clothing", new CategoryData(
                "Apparel and fashion for all occasions",
                new String[]{"Levi's", "Gap", "H&M", "Zara", "Uniqlo", "Ralph Lauren", "Tommy Hilfiger", "Calvin Klein"},
                new String[]{"Classic", "Slim", "Relaxed", "Modern", "Vintage", "Essential", "Premium", "Signature"},
                new String[]{"T-Shirt", "Jeans", "Jacket", "Sweater", "Hoodie", "Dress", "Blouse", "Pants", "Shorts", "Coat"},
                25.0, 150.0, 100, 350
        ));
        CATEGORY_DATA.put("Shoes", new CategoryData(
                "Footwear for every style and activity",
                new String[]{"Nike", "Adidas", "New Balance", "Converse", "Vans", "Puma", "Reebok", "Skechers"},
                new String[]{"Classic", "Air", "Ultra", "Boost", "Flex", "Lite", "Pro", "Max"},
                new String[]{"Sneakers", "Running Shoes", "Boots", "Sandals", "Loafers", "Slip-Ons", "High Tops", "Trainers", "Flats", "Heels"},
                40.0, 200.0, 80, 250
        ));
        CATEGORY_DATA.put("Beauty", new CategoryData(
                "Beauty products and skincare essentials",
                new String[]{"L'Oreal", "Maybelline", "Neutrogena", "Olay", "Clinique", "Estee Lauder", "MAC", "NYX"},
                new String[]{"Professional", "Natural", "Hydrating", "Revitalizing", "Anti-Aging", "Brightening", "Nourishing", "Gentle"},
                new String[]{"Moisturizer", "Serum", "Cleanser", "Toner", "Mask", "Lipstick", "Foundation", "Mascara", "Eyeshadow", "Blush"},
                10.0, 80.0, 150, 400
        ));
        CATEGORY_DATA.put("Toys", new CategoryData(
                "Toys and games for all ages",
                new String[]{"LEGO", "Hasbro", "Mattel", "Fisher-Price", "Nerf", "Hot Wheels", "Barbie", "Play-Doh"},
                new String[]{"Classic", "Deluxe", "Ultimate", "Mega", "Super", "Junior", "Creative", "Interactive"},
                new String[]{"Building Set", "Action Figure", "Board Game", "Puzzle", "Doll", "Car Set", "Craft Kit", "Plush Toy", "Remote Control", "Educational Toy"},
                15.0, 100.0, 100, 300
        ));
        CATEGORY_DATA.put("Stationery", new CategoryData(
                "Office and school stationery supplies",
                new String[]{"Staples", "3M", "Sharpie", "Post-it", "Pilot", "Moleskine", "Faber-Castell", "Crayola"},
                new String[]{"Professional", "Classic", "Premium", "Essential", "Deluxe", "Creative", "Eco", "Ultra"},
                new String[]{"Notebook", "Pen Set", "Marker Set", "Sticky Notes", "Folder", "Binder", "Highlighter", "Pencil Set", "Eraser Pack", "Tape Dispenser"},
                5.0, 40.0, 200, 500
        ));
        CATEGORY_DATA.put("Automotive", new CategoryData(
                "Automotive parts and car accessories",
                new String[]{"Bosch", "Michelin", "3M", "Armor All", "Meguiar's", "WeatherTech", "Garmin", "Pioneer"},
                new String[]{"Premium", "Professional", "Heavy-Duty", "Universal", "Custom", "Performance", "All-Weather", "Ultra"},
                new String[]{"Floor Mat", "Phone Mount", "Dash Cam", "Seat Cover", "Air Freshener", "Wiper Blade", "Car Charger", "Trunk Organizer", "Sun Shade", "Cleaning Kit"},
                15.0, 150.0, 80, 250
        ));
        CATEGORY_DATA.put("Pet Supplies", new CategoryData(
                "Pet food, toys, and accessories",
                new String[]{"Purina", "Blue Buffalo", "Pedigree", "Kong", "Nylabone", "PetSafe", "Friskies", "Greenies"},
                new String[]{"Premium", "Natural", "Organic", "Grain-Free", "Healthy", "Deluxe", "Interactive", "Durable"},
                new String[]{"Dog Food", "Cat Food", "Pet Toy", "Pet Bed", "Collar", "Leash", "Treats", "Grooming Kit", "Water Bowl", "Carrier"},
                10.0, 80.0, 150, 400
        ));
        CATEGORY_DATA.put("Baby Care", new CategoryData(
                "Baby essentials and childcare products",
                new String[]{"Pampers", "Huggies", "Johnson's", "Gerber", "Graco", "Fisher-Price", "Chicco", "Enfamil"},
                new String[]{"Gentle", "Sensitive", "Natural", "Premium", "Organic", "Soft", "Safe", "Comfort"},
                new String[]{"Diapers", "Wipes", "Formula", "Baby Food", "Bottle", "Pacifier", "Blanket", "Onesie", "Stroller", "Car Seat"},
                15.0, 200.0, 100, 350
        ));
        CATEGORY_DATA.put("Garden", new CategoryData(
                "Garden tools and outdoor plants",
                new String[]{"Scotts", "Miracle-Gro", "Fiskars", "Black & Decker", "Husqvarna", "Gardena", "Ortho", "Burpee"},
                new String[]{"Professional", "Heavy-Duty", "Ergonomic", "Premium", "Organic", "All-Purpose", "Compact", "Durable"},
                new String[]{"Shovel", "Rake", "Pruner", "Hose", "Sprinkler", "Planter", "Soil Mix", "Fertilizer", "Seeds Pack", "Gloves"},
                10.0, 100.0, 100, 300
        ));
        CATEGORY_DATA.put("Tools", new CategoryData(
                "Hand tools and power tools for DIY",
                new String[]{"DeWalt", "Milwaukee", "Makita", "Bosch", "Stanley", "Craftsman", "Black & Decker", "Ryobi"},
                new String[]{"Professional", "Heavy-Duty", "Cordless", "Compact", "Premium", "Industrial", "Precision", "Multi-Purpose"},
                new String[]{"Drill", "Saw", "Hammer", "Screwdriver Set", "Wrench Set", "Pliers", "Level", "Tape Measure", "Tool Box", "Sander"},
                20.0, 300.0, 60, 200
        ));
        CATEGORY_DATA.put("Office", new CategoryData(
                "Office furniture and supplies",
                new String[]{"Herman Miller", "Steelcase", "HON", "Fellowes", "Logitech", "HP", "Brother", "Epson"},
                new String[]{"Ergonomic", "Executive", "Adjustable", "Professional", "Compact", "Modern", "Classic", "Premium"},
                new String[]{"Desk Chair", "Standing Desk", "Monitor Stand", "Desk Lamp", "File Cabinet", "Whiteboard", "Shredder", "Printer", "Scanner", "Desk Organizer"},
                30.0, 400.0, 40, 150
        ));
        CATEGORY_DATA.put("Gaming", new CategoryData(
                "Video games and gaming accessories",
                new String[]{"Razer", "Logitech G", "SteelSeries", "HyperX", "Corsair", "ASUS ROG", "MSI", "Turtle Beach"},
                new String[]{"Pro", "Elite", "Ultimate", "RGB", "Wireless", "Mechanical", "Gaming", "Tournament"},
                new String[]{"Gaming Mouse", "Gaming Keyboard", "Headset", "Controller", "Mouse Pad", "Gaming Chair", "Capture Card", "Webcam", "Microphone", "Monitor"},
                30.0, 400.0, 50, 180
        ));
        CATEGORY_DATA.put("Music", new CategoryData(
                "Musical instruments and audio equipment",
                new String[]{"Fender", "Gibson", "Yamaha", "Roland", "Shure", "Audio-Technica", "Sennheiser", "Korg"},
                new String[]{"Professional", "Studio", "Classic", "Vintage", "Digital", "Acoustic", "Electric", "Portable"},
                new String[]{"Guitar", "Keyboard", "Microphone", "Headphones", "Amplifier", "Drum Pad", "Audio Interface", "MIDI Controller", "Speakers", "Cables"},
                50.0, 500.0, 40, 150
        ));
        CATEGORY_DATA.put("Travel", new CategoryData(
                "Travel gear and luggage",
                new String[]{"Samsonite", "Tumi", "Away", "Travelpro", "Osprey", "Eagle Creek", "Briggs & Riley", "Delsey"},
                new String[]{"Carry-On", "Expandable", "Lightweight", "Hardside", "Softside", "Premium", "Compact", "Durable"},
                new String[]{"Suitcase", "Backpack", "Duffel Bag", "Travel Pillow", "Packing Cubes", "Toiletry Bag", "Passport Holder", "Luggage Tag", "Travel Adapter", "Neck Wallet"},
                20.0, 300.0, 60, 200
        ));
        CATEGORY_DATA.put("Furniture", new CategoryData(
                "Home and office furniture",
                new String[]{"IKEA", "Ashley", "Wayfair", "West Elm", "Pottery Barn", "Crate & Barrel", "CB2", "Article"},
                new String[]{"Modern", "Classic", "Rustic", "Contemporary", "Minimalist", "Industrial", "Scandinavian", "Mid-Century"},
                new String[]{"Sofa", "Coffee Table", "Bookshelf", "Dining Table", "Bed Frame", "Dresser", "Nightstand", "TV Stand", "Ottoman", "Accent Chair"},
                100.0, 800.0, 20, 80
        ));
        CATEGORY_DATA.put("Decor", new CategoryData(
                "Home decor and decorative accessories",
                new String[]{"Pottery Barn", "West Elm", "Anthropologie", "Target", "HomeGoods", "Pier 1", "World Market", "Crate & Barrel"},
                new String[]{"Artisan", "Handcrafted", "Vintage", "Modern", "Bohemian", "Coastal", "Farmhouse", "Elegant"},
                new String[]{"Vase", "Picture Frame", "Wall Art", "Candle", "Throw Pillow", "Mirror", "Clock", "Sculpture", "Rug", "Curtains"},
                15.0, 150.0, 80, 250
        ));
        CATEGORY_DATA.put("Lighting", new CategoryData(
                "Indoor and outdoor lighting solutions",
                new String[]{"Philips Hue", "GE", "Lutron", "Cree", "Feit Electric", "Hampton Bay", "Progress Lighting", "Kichler"},
                new String[]{"Smart", "LED", "Dimmable", "Energy-Saving", "Vintage", "Modern", "Industrial", "Ambient"},
                new String[]{"Table Lamp", "Floor Lamp", "Pendant Light", "Chandelier", "Wall Sconce", "String Lights", "Desk Lamp", "Night Light", "Spotlight", "Bulb Pack"},
                15.0, 200.0, 80, 250
        ));
        CATEGORY_DATA.put("Health", new CategoryData(
                "Health and wellness products",
                new String[]{"Omron", "Braun", "Philips", "Oral-B", "Waterpik", "Theragun", "Withings", "iHealth"},
                new String[]{"Professional", "Digital", "Smart", "Precision", "Portable", "Advanced", "Clinical", "Home"},
                new String[]{"Blood Pressure Monitor", "Thermometer", "Scale", "Pulse Oximeter", "Heating Pad", "Massager", "First Aid Kit", "Humidifier", "Air Purifier", "Sleep Aid"},
                20.0, 200.0, 80, 250
        ));
        CATEGORY_DATA.put("Supplements", new CategoryData(
                "Vitamins and dietary supplements",
                new String[]{"Nature Made", "Centrum", "Garden of Life", "NOW Foods", "Optimum Nutrition", "GNC", "Vital Proteins", "Nordic Naturals"},
                new String[]{"Organic", "Natural", "High-Potency", "Plant-Based", "Premium", "Essential", "Advanced", "Complete"},
                new String[]{"Multivitamin", "Vitamin D", "Omega-3", "Protein Powder", "Probiotics", "Collagen", "Vitamin C", "Magnesium", "Iron", "B-Complex"},
                10.0, 60.0, 150, 400
        ));
        CATEGORY_DATA.put("Outdoors", new CategoryData(
                "Outdoor recreation and camping gear",
                new String[]{"Coleman", "REI", "The North Face", "Patagonia", "Yeti", "Kelty", "MSR", "Black Diamond"},
                new String[]{"All-Weather", "Ultralight", "Expedition", "Compact", "Durable", "Insulated", "Waterproof", "Professional"},
                new String[]{"Tent", "Sleeping Bag", "Camping Chair", "Cooler", "Lantern", "Hiking Poles", "Water Bottle", "Backpack", "Hammock", "Camp Stove"},
                25.0, 300.0, 60, 200
        ));
        CATEGORY_DATA.put("Cycling", new CategoryData(
                "Bicycles and cycling accessories",
                new String[]{"Trek", "Specialized", "Giant", "Cannondale", "Schwinn", "Shimano", "Garmin", "Bell"},
                new String[]{"Pro", "Elite", "Performance", "Urban", "Mountain", "Road", "Hybrid", "Electric"},
                new String[]{"Bike Helmet", "Bike Lock", "Bike Light", "Cycling Gloves", "Water Bottle Cage", "Bike Pump", "Saddle Bag", "Bike Computer", "Pedals", "Handlebar Tape"},
                15.0, 200.0, 60, 200
        ));
        CATEGORY_DATA.put("Mobile Accessories", new CategoryData(
                "Smartphone and tablet accessories",
                new String[]{"Anker", "Belkin", "Spigen", "OtterBox", "Mophie", "PopSockets", "Zagg", "RhinoShield"},
                new String[]{"Ultra", "Slim", "Rugged", "Clear", "Wireless", "Fast", "Premium", "Pro"},
                new String[]{"Phone Case", "Screen Protector", "Charger", "Power Bank", "Car Mount", "Wireless Charger", "Cable", "Stand", "Earbuds", "Stylus"},
                10.0, 80.0, 150, 400
        ));
        CATEGORY_DATA.put("Computers", new CategoryData(
                "Computer hardware and peripherals",
                new String[]{"Dell", "HP", "Lenovo", "ASUS", "Acer", "Apple", "Microsoft", "Intel"},
                new String[]{"Pro", "Gaming", "Business", "Ultra", "Slim", "Portable", "High-Performance", "Budget"},
                new String[]{"Laptop", "Desktop", "Monitor", "SSD", "RAM", "Graphics Card", "Keyboard", "Mouse", "Webcam", "USB Hub"},
                50.0, 1000.0, 30, 120
        ));
    }

    @Getter
    public static class CategoryData {
        private final String description;
        private final String[] brands;
        private final String[] adjectives;
        private final String[] productTypes;
        private final double minPrice;
        private final double maxPrice;
        private final int minStock;
        private final int maxStock;

        public CategoryData(String description, String[] brands, String[] adjectives, String[] productTypes,
                            double minPrice, double maxPrice, int minStock, int maxStock) {
            this.description = description;
            this.brands = brands;
            this.adjectives = adjectives;
            this.productTypes = productTypes;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.minStock = minStock;
            this.maxStock = maxStock;
        }
    }

    public static class GeneratedData {
        @Getter
        private final List<Category> categories;
        @Getter
        private final List<Product> products;
        @Getter
        private final List<Inventory> inventories;

        public GeneratedData(List<Category> categories, List<Product> products, List<Inventory> inventories) {
            this.categories = categories;
            this.products = products;
            this.inventories = inventories;
        }
    }

    public GeneratedData generate(long seed, int productCount) {
        Random random = new Random(seed);

        List<Category> categories = generateCategories();
        List<Product> products = new ArrayList<>();
        List<Inventory> inventories = new ArrayList<>();

        Instant now = Instant.now();
        Instant eighteenMonthsAgo = now.minus(18 * 30, ChronoUnit.DAYS);
        long timeRangeSeconds = now.getEpochSecond() - eighteenMonthsAgo.getEpochSecond();

        List<String> categoryNames = new ArrayList<>(CATEGORY_DATA.keySet());
        int productsPerCategory = productCount / categories.size();
        int remainder = productCount % categories.size();

        int productIndex = 0;
        for (int catIdx = 0; catIdx < categories.size(); catIdx++) {
            Category category = categories.get(catIdx);
            String categoryName = categoryNames.get(catIdx);
            CategoryData catData = CATEGORY_DATA.get(categoryName);

            int productsForThisCategory = productsPerCategory + (catIdx < remainder ? 1 : 0);

            for (int i = 0; i < productsForThisCategory; i++) {
                productIndex++;
                String productId = UUID.randomUUID().toString();
                String categoryCode = categoryName.substring(0, Math.min(4, categoryName.length())).toUpperCase().replaceAll("[^A-Z]", "");
                if (categoryCode.length() < 3) {
                    categoryCode = categoryName.toUpperCase().replaceAll("[^A-Z]", "").substring(0, Math.min(3, categoryName.replaceAll("[^A-Za-z]", "").length()));
                }
                String sku = categoryCode + "-" + String.format("%05d", productIndex);

                String brand = catData.getBrands()[random.nextInt(catData.getBrands().length)];
                String adjective = catData.getAdjectives()[random.nextInt(catData.getAdjectives().length)];
                String productType = catData.getProductTypes()[random.nextInt(catData.getProductTypes().length)];
                String name = brand + " " + adjective + " " + productType;

                String description = generateDescription(random, brand, adjective, productType, categoryName);

                double priceRange = catData.getMaxPrice() - catData.getMinPrice();
                double price = catData.getMinPrice() + (random.nextDouble() * priceRange);
                BigDecimal priceDecimal = BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);

                boolean active = random.nextDouble() < 0.9;

                long randomSeconds = (long) (random.nextDouble() * timeRangeSeconds);
                Instant createdAt = eighteenMonthsAgo.plusSeconds(randomSeconds);
                long updateOffset = (long) (random.nextDouble() * (now.getEpochSecond() - createdAt.getEpochSecond()));
                Instant updatedAt = createdAt.plusSeconds(updateOffset);

                Product product = Product.builder()
                        .productId(productId)
                        .sku(sku)
                        .name(name)
                        .description(description)
                        .categoryId(category.getCategoryId())
                        .price(priceDecimal)
                        .currency("USD")
                        .active(active)
                        .createdAt(createdAt)
                        .updatedAt(updatedAt)
                        .build();
                products.add(product);

                int stockQty = generateStockQuantity(random, catData);
                int reservedQty = stockQty > 0 ? random.nextInt(Math.min(50, stockQty) + 1) : 0;

                Inventory inventory = Inventory.builder()
                        .productId(productId)
                        .stockQty(stockQty)
                        .reservedQty(reservedQty)
                        .build();
                inventories.add(inventory);
            }
        }

        return new GeneratedData(categories, products, inventories);
    }

    private List<Category> generateCategories() {
        List<Category> categories = new ArrayList<>();
        for (Map.Entry<String, CategoryData> entry : CATEGORY_DATA.entrySet()) {
            Category category = Category.builder()
                    .categoryId(UUID.randomUUID().toString())
                    .name(entry.getKey())
                    .description(entry.getValue().getDescription())
                    .build();
            categories.add(category);
        }
        return categories;
    }

    private String generateDescription(Random random, String brand, String adjective, String productType, String categoryName) {
        String[] templates = {
                "The %s %s %s offers exceptional quality and performance for %s enthusiasts.",
                "Experience the %s %s %s, designed with premium materials and innovative features.",
                "This %s %s %s combines style and functionality, perfect for everyday use.",
                "Discover the %s %s %s featuring advanced technology and superior craftsmanship.",
                "The %s %s %s delivers outstanding value with its durable construction and modern design."
        };
        String template = templates[random.nextInt(templates.length)];
        return String.format(template, brand, adjective.toLowerCase(), productType.toLowerCase(), categoryName.toLowerCase());
    }

    private int generateStockQuantity(Random random, CategoryData catData) {
        double stockRoll = random.nextDouble();
        if (stockRoll < 0.10) {
            return 0;
        } else if (stockRoll < 0.25) {
            return 1 + random.nextInt(5);
        } else {
            int range = catData.getMaxStock() - catData.getMinStock();
            return catData.getMinStock() + random.nextInt(range + 1);
        }
    }

    public static int getCategoryCount() {
        return CATEGORY_DATA.size();
    }
}
