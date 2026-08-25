package com.secondhand.config;

import com.secondhand.auth.entity.*;
import com.secondhand.auth.repository.UserIdentityRepository;
import com.secondhand.auth.repository.UserRepository;
import com.secondhand.chat.entity.ChatMessage;
import com.secondhand.chat.repository.ChatMessageRepository;
import com.secondhand.comment.entity.Comment;
import com.secondhand.comment.repository.CommentRepository;
import com.secondhand.favorite.entity.Favorite;
import com.secondhand.favorite.repository.FavoriteRepository;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.product.category.entity.Category;
import com.secondhand.product.category.repository.CategoryRepository;
import com.secondhand.product.entity.Product;
import com.secondhand.product.entity.ProductCondition;
import com.secondhand.product.entity.ProductStatus;
import com.secondhand.product.image.entity.ProductImage;
import com.secondhand.product.image.repository.ProductImageRepository;
import com.secondhand.product.repository.ProductRepository;
import com.secondhand.rating.entity.Rating;
import com.secondhand.rating.repository.RatingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepo;
    private final UserIdentityRepository identityRepo;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepo;
    private final ProductRepository productRepo;
    private final ProductImageRepository productImageRepo;
    private final OrderRepository orderRepo;
    private final CommentRepository commentRepo;
    private final FavoriteRepository favoriteRepo;
    private final RatingRepository ratingRepo;
    private final ChatMessageRepository chatMessageRepo;

    public DataSeeder(UserRepository userRepo, UserIdentityRepository identityRepo,
                      PasswordEncoder passwordEncoder, CategoryRepository categoryRepo,
                      ProductRepository productRepo, ProductImageRepository productImageRepo,
                      OrderRepository orderRepo, CommentRepository commentRepo,
                      FavoriteRepository favoriteRepo, RatingRepository ratingRepo,
                      ChatMessageRepository chatMessageRepo) {
        this.userRepo = userRepo; this.identityRepo = identityRepo;
        this.passwordEncoder = passwordEncoder; this.categoryRepo = categoryRepo;
        this.productRepo = productRepo; this.productImageRepo = productImageRepo;
        this.orderRepo = orderRepo; this.commentRepo = commentRepo;
        this.favoriteRepo = favoriteRepo; this.ratingRepo = ratingRepo;
        this.chatMessageRepo = chatMessageRepo;
    }

    // 分类名必须与 CategoryService.seedCategories() 中的一致
    // 每个分类 3-5 件商品，共约 85-90 件
    // {title, priceYuan, conditionOrdinal, freeShipping(1/0)}
    private static final Object[][] TEMPLATES = {
        // ===== 手机通讯 (5件) =====
        {"手机通讯", "iPhone 15 Pro Max 256GB 原色钛金属 99新 电池97%", 7599, 1, 1},
        {"手机通讯", "华为 Mate 60 Pro 雅丹黑 12+512GB 在保", 5499, 1, 1},
        {"手机通讯", "小米14 Ultra 钛金属版 16+1TB 用了俩月", 4599, 1, 0},
        {"手机通讯", "一加 Ace 3 Pro 24+1TB 钛空镜银 99新", 2599, 2, 1},
        {"手机通讯", "Samsung S24 Ultra 钛灰 12+256GB 港版带票", 5299, 2, 1},

        // ===== 电脑办公 (5件) =====
        {"电脑办公", "华为 MateBook X Pro 2024 i7/32G/1T 微绒白", 8999, 2, 0},
        {"电脑办公", "联想 ThinkPad X1 Carbon Gen11 i7/16G/512G", 4999, 2, 1},
        {"电脑办公", "MacBook Air M3 15英寸 8G/256G 午夜色", 6899, 2, 0},
        {"电脑办公", "iPad Pro M4 11英寸 256GB WiFi 深空黑", 6299, 1, 1},
        {"电脑办公", "华为 MatePad Pro 13.2 英寸 12+256G 星闪键盘", 3299, 2, 1},

        // ===== 数码影音 (4件) =====
        {"数码影音", "Sony A7M4 全画幅微单 快门8000 国行带票", 12500, 2, 1},
        {"数码影音", "富士 X-T5 银色 单机身 快门3000 箱说全", 8999, 2, 1},
        {"数码影音", "DJI Osmo Pocket 3 全能套装 用了两次", 2999, 1, 1},
        {"数码影音", "Canon RF 24-70mm F2.8 L 镜头 98新 在保", 12800, 1, 0},

        // ===== 家用电器 (4件) =====
        {"家用电器", "戴森 V12 Detect Slim 无线吸尘器 激光探测", 2499, 2, 1},
        {"家用电器", "Dyson Hot+Cool 空气净化冷暖风扇 HP09 银白", 3699, 2, 0},
        {"家用电器", "石头 G20 自清洁扫地机器人 用了三个月", 2899, 2, 1},
        {"家用电器", "小米米家空气炸锅 Pro 6.5L 可视窗 全新未拆", 249, 0, 1},

        // ===== 家具家装 (4件) =====
        {"家具家装", "林氏家居 北欧简约布艺沙发 三人位2.1米 科技布", 1680, 3, 0},
        {"家具家装", "源氏木语 全实木床 1.8米橡木 含床头柜*2", 2200, 2, 0},
        {"家具家装", "乐歌 E5 电动升降桌 1.4米 双电机 白色", 1299, 2, 0},
        {"家具家装", "宜家 PAX 帕克思 定制衣柜 236cm白色 含配件", 1800, 3, 0},

        // ===== 家居日用 (4件) =====
        {"家居日用", "设计师款 北欧落地氛围灯 胡桃木+米白灯罩 三色温", 268, 2, 1},
        {"家居日用", "霜山 可折叠收纳箱66L 3个装 米白 全新", 129, 3, 1},
        {"家居日用", "野兽派 莫奈花园 香薰蜡烛礼盒 睡莲系列 全新", 168, 2, 1},
        {"家居日用", "Zara Home 纯棉水洗棉四件套 1.8m床 亚麻色", 320, 2, 1},

        // ===== 男装 (4件) =====
        {"男装", "始祖鸟 Beta LT 冲锋衣 男L码 黑色 Gore-Tex", 3200, 2, 1},
        {"男装", "优衣库 +J 联名 羊毛混纺大衣 男M码 深灰 绝版", 550, 2, 1},
        {"男装", "Ralph Lauren 牛津纺衬衫 男M码 天蓝 经典款", 680, 1, 1},
        {"男装", "Nike ACG 工装长裤 男L码 卡其 防泼水 春季", 399, 2, 1},

        // ===== 女装 (4件) =====
        {"女装", "Max Mara Weekend 羊毛大衣 女S码 驼色 意产", 2600, 2, 0},
        {"女装", "Lululemon Define Jacket 女S码 黑 Nulu面料", 680, 2, 1},
        {"女装", "ZARA 法式碎花连衣裙 女M码 春夏 全新带吊牌", 159, 0, 1},
        {"女装", "COS 极简风阔腿裤 女M码 羊毛混纺 九分裤", 420, 2, 1},

        // ===== 鞋靴箱包 (4件) =====
        {"鞋靴箱包", "Nike AJ1 Retro High OG 芝加哥 42码 仅试穿", 1899, 2, 1},
        {"鞋靴箱包", "Salomon XT-6 越野跑鞋 灰白 43码 上脚3次", 899, 2, 1},
        {"鞋靴箱包", "Coach 蔻驰 Tabby 26 白色 链条包 荔枝纹牛皮", 1680, 1, 1},
        {"鞋靴箱包", "TUMI Alpha Bravo 双肩包 弹道尼龙 15-16寸", 1800, 3, 0},

        // ===== 珠宝配饰 (3件) =====
        {"珠宝配饰", "Gucci GG Marmont 双面腰带 3cm 85cm 黑色", 1200, 2, 0},
        {"珠宝配饰", "Ray-Ban Aviator 飞行员偏光太阳镜 58mm 经典款", 580, 2, 1},
        {"珠宝配饰", "Pandora 璀璨之心手链 17cm 含5颗串珠 礼盒装", 780, 2, 1},

        // ===== 美妆护肤 (3件) =====
        {"美妆护肤", "La Mer 海蓝之谜 经典面霜 60ml 全新未拆 日上", 1280, 0, 1},
        {"美妆护肤", "Tom Ford 黑管唇釉 #100 全新 限定色", 260, 0, 1},
        {"美妆护肤", "Aesop 伊索 香芹籽抗氧化精华 100ml 全新", 450, 0, 1},

        // ===== 母婴亲子 (4件) =====
        {"母婴亲子", "Babybjörn One Air 婴儿背带 0-3岁 深灰 透气款", 899, 2, 1},
        {"母婴亲子", "stokke Tripp Trapp 成长椅 完整套件 含新生儿座", 1600, 2, 0},
        {"母婴亲子", "Avent 新安怡 双边电动吸奶器 SCF395 礼盒 99新", 599, 1, 1},
        {"母婴亲子", "Merries 花王妙而舒 拉拉裤 XL 136片 未拆封", 180, 0, 1},

        // ===== 运动户外 (4件) =====
        {"运动户外", "YONEX 天斧100ZZ 羽毛球拍 4UG5 CH版 古红", 899, 2, 1},
        {"运动户外", "探路者 三季帐篷 双人 含地钉防潮垫 露营", 399, 3, 1},
        {"运动户外", "Bowflex 可调节哑铃 552i 2-24kg 一对 家用", 1200, 2, 1},
        {"运动户外", "NatureHike 挪客 羽绒睡袋 800蓬 鹅绒 -5℃", 580, 2, 1},

        // ===== 图书音像 (4件) =====
        {"图书音像", "三体 全套三册 刘慈欣 精装典藏版 硬壳封套", 68, 3, 1},
        {"图书音像", "百年孤独 50周年纪念版 马尔克斯 全新未拆", 45, 0, 1},
        {"图书音像", "灌篮高手 完全版 1-24卷 井上雄彦 台版", 320, 2, 1},
        {"图书音像", "金庸全集 36册 明河社修订版 九成新 收藏级", 680, 2, 1},

        // ===== 食品生鲜 (3件) =====
        {"食品生鲜", "正官庄 高丽参精 EVERYTIME 10ml*30包 全新", 280, 0, 1},
        {"食品生鲜", "八马 特级安溪铁观音 礼盒装 250g 未开封", 388, 0, 1},
        {"食品生鲜", "Swisse 护肝片 奶蓟草 120粒*2瓶 全新未拆", 168, 0, 1},

        // ===== 医药保健 (3件) =====
        {"医药保健", "欧姆龙 J750 电子血压计 上臂式 用了两次", 280, 2, 1},
        {"医药保健", "鱼跃 便携制氧机 3L 家用 老人 低噪音款", 1680, 2, 1},
        {"医药保健", "倍轻松 iDream5S 眼部按摩仪 热敷 气囊揉捏", 460, 2, 1},

        // ===== 汽车用品 (3件) =====
        {"汽车用品", "Mio MiVue 798 行车记录仪 2K 前后双录 全新", 580, 0, 1},
        {"汽车用品", "壳牌 先锋超凡喜力 0W-20 SP机油 4L装 全新", 280, 0, 1},
        {"汽车用品", "小米米家车载空气净化器 除甲醛PM2.5 用了三月", 199, 2, 1},

        // ===== 宠物生活 (3件) =====
        {"宠物生活", "皇家 英短专用猫粮 10kg 未拆封 日期到26年6月", 380, 0, 1},
        {"宠物生活", "小佩 智能全自动猫砂盆 MAX2 除臭款 用了俩月", 899, 2, 0},
        {"宠物生活", "Purrre 实木猫爬架 1.8米 剑麻柱 太空舱+吊床", 350, 2, 0},

        // ===== 文玩收藏 (3件) =====
        {"文玩收藏", "景德镇手绘青花瓷花瓶 20cm 附干花一束 礼盒", 120, 3, 1},
        {"文玩收藏", "紫檀木手串 2.0cm 12颗 金星小叶紫檀 盘玩半年", 680, 2, 1},
        {"文玩收藏", "长城币 1981年 精制套装 7枚 带盒证 评级币", 980, 2, 0},

        // ===== 乐器/音乐 (4件) =====
        {"乐器/音乐", "Yamaha F310 民谣吉他 41寸 入门神器 弦距已调", 499, 3, 1},
        {"乐器/音乐", "Roland FP-30X 电钢琴 88键重锤 PHA4 含琴架", 3200, 2, 0},
        {"乐器/音乐", "Taylor GS Mini-e Koa 36寸 旅行吉他 相思木", 3800, 1, 1},
        {"乐器/音乐", "Marshall Stanmore III 蓝牙音箱 经典黑 家用", 1899, 2, 0},

        // ===== 潮玩/模型 (3件) =====
        {"潮玩/模型", "LEGO 10328 玫瑰花束 拼搭完成 含透明展示盒", 258, 3, 1},
        {"潮玩/模型", "POP MART Skullpanda 温度系列 全套12款 带盒", 580, 2, 1},
        {"潮玩/模型", "万代 PG Unleashed RX-78-2 高达 素组 渗线渍洗", 680, 3, 0},

        // ===== 游戏/电竞 (3件) =====
        {"游戏/电竞", "PS5 国行光驱版 双手柄 含蜘蛛侠2+黑神话悟空", 3200, 2, 0},
        {"游戏/电竞", "Nintendo Switch OLED 王国之泪限定版 512G卡", 1899, 2, 1},
        {"游戏/电竞", "罗技 G Pro X Superlight 2 无线鼠标 白 用了1月", 680, 1, 1},

        // ===== 票券/其他 (3件) =====
        {"票券/其他", "环球影城 双人票 平日 有效期到年底 电子票", 598, 0, 1},
        {"票券/其他", "爱奇艺黄金会员 年卡 兑换码 未激活 可直充", 158, 0, 1},
        {"票券/其他", "盒马礼品卡 500元 实体卡 未激活 可查余额", 480, 0, 1},
    };

    // ================================================================
    //  主流程
    // ================================================================
    @Override
    public void run(String... args) {
        try {
            if (identityRepo.findByIdentityTypeAndIdentifier(IdentityType.PHONE, "13800000001").isPresent()) {
                log.info("[DataSeeder] 演示数据已存在，跳过");
                return;
            }
        } catch (Exception e) { log.warn("[DataSeeder] 检查失败: {}", e.getMessage()); }

        log.info("[DataSeeder] ========== 开始生成演示数据 ==========");
        LocalDateTime now = LocalDateTime.now();
        String pw = passwordEncoder.encode("123456");

        // CategoryService 的 @PostConstruct 已经创建好 23 个分类，直接加载
        List<Category> categories = safeStep("加载分类", () -> {
            List<Category> all = categoryRepo.findAll();
            log.info("[DataSeeder] 加载 {} 个分类", all.size());
            return all;
        });
        if (categories == null || categories.isEmpty()) { log.error("[DataSeeder] 无分类!"); return; }

        List<User> users = safeStep("用户", () -> seedUsers(pw, now));
        if (users == null || users.isEmpty()) { log.error("[DataSeeder] 无用户!"); return; }

        List<Product> products = safeStep("商品", () -> seedProducts(categories, users, now));
        if (products == null || products.isEmpty()) { log.error("[DataSeeder] 无商品!"); return; }

        safeStep("图片", () -> { seedImages(products); return null; });
        List<Order> orders = safeStep("订单", () -> seedOrders(users, products, now));
        safeStep("评论", () -> { seedComments(users, products, now); return null; });
        safeStep("收藏", () -> { seedFavorites(users, products, now); return null; });
        safeStep("评价", () -> { seedRatings(orders, now); return null; });
        safeStep("聊天", () -> { seedChats(users, products, now); return null; });

        log.info("[DataSeeder] ✅ 完成！{}用户 {}分类 {}商品 {}订单",
                users.size(), categories.size(), products.size(),
                orders != null ? orders.size() : 0);
        log.info("[DataSeeder] 演示用户密码均为: 123456");
    }

    private <T> T safeStep(String name, java.util.function.Supplier<T> fn) {
        try { T r = fn.get(); log.info("[DataSeeder] ✅ {}: 完成", name); return r; }
        catch (Exception e) { log.error("[DataSeeder] ❌ {} 失败: {}", name, e.getMessage(), e); return null; }
    }

    // ================================================================
    //  用户
    // ================================================================
    private List<User> seedUsers(String pw, LocalDateTime now) {
        String[][] defs = {
            {"小明同学","13800000001","xiaoming"}, {"淘宝达人","13800000002","daren"},
            {"数码控",  "13800000003","shuma"},    {"书香门第","13800000004","shuxiang"},
            {"运动健将","13800000005","yundong"},   {"时尚达人","13800000006","shishang"},
            {"家居爱好者","13800000007","jiaju"},    {"音乐之声","13800000008","yinyue"},
        };
        List<User> list = new ArrayList<>();
        for (String[] d : defs) {
            if (identityRepo.findByIdentityTypeAndIdentifier(IdentityType.PHONE, d[1]).isPresent()) {
                list.add(identityRepo.findByIdentityTypeAndIdentifier(IdentityType.PHONE, d[1]).get().getUser());
                continue;
            }
            User u = new User();
            u.setNickname(d[0]); u.setPasswordHash(pw);
            u.setStatus(UserStatus.ACTIVE); u.setRole(Role.USER); u.setPhone(d[1]);
            u.setAvatarUrl("https://api.dicebear.com/9.x/avataaars/svg?seed=" + d[2]);
            u.setCreatedAt(now.minusDays(20 + list.size())); u.setUpdatedAt(now);
            u = userRepo.save(u); list.add(u);

            UserIdentity id = new UserIdentity();
            id.setUser(u); id.setIdentityType(IdentityType.PHONE); id.setIdentifier(d[1]);
            id.setVerified(true); id.setCreatedAt(u.getCreatedAt()); id.setUpdatedAt(now);
            identityRepo.save(id);
        }
        return list;
    }

    // ================================================================
    //  商品
    // ================================================================
    private List<Product> seedProducts(List<Category> categories, List<User> users, LocalDateTime now) {
        // 建立 分类名 → ID 映射
        Map<String, Long> nameToId = new HashMap<>();
        for (Category c : categories) nameToId.put(c.getName(), c.getId());

        List<Product> list = new ArrayList<>();
        Random rnd = new Random();
        int catCount = categories.size();

        for (int i = 0; i < TEMPLATES.length; i++) {
            Object[] t = TEMPLATES[i];
            String catName = (String) t[0];
            String title = (String) t[1];
            int priceYuan = (int) t[2];
            int condOrd = (int) t[3];
            boolean freeShip = (int) t[4] == 1;

            Long catId = nameToId.get(catName);
            User seller = users.get(i % users.size());

            Product p = new Product();
            p.setTitle(title);
            p.setPriceCent(priceYuan * 100);
            p.setCategoryId(catId);
            p.setCondition(ProductCondition.values()[condOrd]);
            p.setStatus(ProductStatus.ON_SALE);
            p.setQuantity(1);
            p.setFreeShipping(freeShip);
            p.setShippingFeeCent(freeShip ? 0 : 1500);
            p.setSellerId(seller.getId());
            p.setDescription("二手闲置，功能正常，成色如图。有意私聊，看到就回。");
            p.setCreatedAt(now.minusDays(rnd.nextInt(28) + 1));
            p.setUpdatedAt(p.getCreatedAt());
            p = productRepo.save(p);
            list.add(p);
        }
        return list;
    }

    // ================================================================
    //  图片
    // ================================================================
    private void seedImages(List<Product> products) {
        for (Product p : products) {
            int n = 2 + (int) (Math.random() * 3);
            for (int i = 0; i < n; i++) {
                String seed = "img" + p.getId() + "x" + i;
                ProductImage img = new ProductImage();
                img.setProductId(p.getId());
                img.setUrl("https://picsum.photos/seed/" + seed + "/800/800");
                img.setThumbnailUrl("https://picsum.photos/seed/" + seed + "/200/200");
                img.setSortOrder(i);
                img.setCreatedAt(p.getCreatedAt());
                productImageRepo.save(img);
                if (i == 0) { p.setCoverImageUrl(img.getUrl()); productRepo.save(p); }
            }
        }
    }

    // ================================================================
    //  订单
    // ================================================================
    private List<Order> seedOrders(List<User> users, List<Product> products, LocalDateTime now) {
        List<Order> list = new ArrayList<>();
        Random rnd = new Random();
        List<Product> shuffled = new ArrayList<>(products);
        Collections.shuffle(shuffled, rnd);
        int n = Math.min(10, shuffled.size());
        OrderStatus[] sts = {OrderStatus.COMPLETED,OrderStatus.COMPLETED,OrderStatus.COMPLETED,
                OrderStatus.COMPLETED,OrderStatus.WAIT_DELIVER,OrderStatus.WAIT_DELIVER,
                OrderStatus.WAIT_RECEIVE,OrderStatus.WAIT_RECEIVE,OrderStatus.WAIT_PAY,OrderStatus.WAIT_PAY};

        for (int i = 0; i < n; i++) {
            Product p = shuffled.get(i);
            User buyer = users.get(rnd.nextInt(users.size()));
            if (buyer.getId().equals(p.getSellerId())) buyer = users.get((users.indexOf(buyer)+1)%users.size());
            OrderStatus st = sts[i];
            Order o = new Order();
            o.setProductId(p.getId()); o.setBuyerId(buyer.getId()); o.setSellerId(p.getSellerId());
            o.setAmountCent(p.getPriceCent()); o.setStatus(st);
            o.setReceiverName(buyer.getNickname()); o.setReceiverPhone(buyer.getPhone());
            o.setReceiverAddress("XX省XX市XX区XX路XX号");
            LocalDateTime ot = p.getCreatedAt().plusDays(rnd.nextInt(5)+1);
            o.setCreatedAt(ot); o.setUpdatedAt(ot);
            if (st != OrderStatus.WAIT_PAY) o.setPaidAt(ot);
            if (st == OrderStatus.WAIT_RECEIVE || st == OrderStatus.COMPLETED) o.setShippedAt(ot.plusHours(rnd.nextInt(24)+2));
            if (st == OrderStatus.COMPLETED) o.setCompletedAt(ot.plusDays(rnd.nextInt(7)+3));
            list.add(orderRepo.save(o));
        }
        return list;
    }

    // ================================================================
    //  评论
    // ================================================================
    private void seedComments(List<User> users, List<Product> products, LocalDateTime now) {
        String[] texts = {"还在吗？可以小刀吗？","同城面交OK吗？","好东西帮顶！","成色怎么样？","这个价合适","配件都在吗？","用了多久了？","还在保吗？"};
        Random rnd = new Random();
        for (int i = 0; i < Math.min(20, products.size()); i++) {
            Product p = products.get(i);
            Comment c = new Comment();
            c.setProductId(p.getId());
            c.setUserId(users.get(rnd.nextInt(users.size())).getId());
            c.setContent(texts[rnd.nextInt(texts.length)]);
            c.setCreatedAt(p.getCreatedAt().plusDays(rnd.nextInt(5)+1));
            commentRepo.save(c);
        }
    }

    // ================================================================
    //  收藏
    // ================================================================
    private void seedFavorites(List<User> users, List<Product> products, LocalDateTime now) {
        Random rnd = new Random();
        for (User u : users) {
            List<Product> s = new ArrayList<>(products);
            Collections.shuffle(s, rnd);
            for (int i = 0; i < 3 + rnd.nextInt(3); i++) {
                Favorite f = new Favorite();
                f.setUserId(u.getId()); f.setProductId(s.get(i).getId());
                f.setCreatedAt(now.minusDays(rnd.nextInt(14)+1));
                favoriteRepo.save(f);
            }
        }
    }

    // ================================================================
    //  评价
    // ================================================================
    private void seedRatings(List<Order> orders, LocalDateTime now) {
        if (orders == null) return;
        String[] texts = {"卖家很实在，成色比描述还好，包装认真发货快！","用了几天一切正常，描述准确，好评！","物超所值，二手买到这成色很惊喜。","帮朋友买的，他说很好用，谢谢！","沟通顺畅，发货快，满意！"};
        Random rnd = new Random();
        for (Order o : orders) {
            if (o.getStatus() != OrderStatus.COMPLETED) continue;
            Rating r = new Rating();
            r.setOrderId(o.getId()); r.setProductId(o.getProductId());
            r.setSellerId(o.getSellerId()); r.setReviewerId(o.getBuyerId());
            r.setScore(4 + rnd.nextInt(2));
            r.setComment(texts[rnd.nextInt(texts.length)]);
            r.setCreatedAt(o.getCompletedAt() != null ? o.getCompletedAt().plusDays(1) : now.minusDays(2));
            ratingRepo.save(r);
        }
    }

    // ================================================================
    //  聊天
    // ================================================================
    private void seedChats(List<User> users, List<Product> products, LocalDateTime now) {
        Random rnd = new Random();
        List<Product> s = new ArrayList<>(products);
        Collections.shuffle(s, rnd);
        String[][] convos = {{"你好，还在吗？","在的","能便宜点吗？","最低了亲"},{"成色怎么样？","九成新，没怎么用","好的"},
                {"能面交吗？","可以，地铁站见","周末方便吗？"},{"还在保吗？","在保到明年","我直接拍了"},
                {"发什么快递？","顺丰包邮","好的拍了，尽快发"}};
        for (int i = 0; i < Math.min(5, s.size()); i++) {
            Product p = s.get(i);
            User buyer = users.get(rnd.nextInt(users.size()));
            if (buyer.getId().equals(p.getSellerId())) buyer = users.get((users.indexOf(buyer)+1)%users.size());
            User seller = users.stream().filter(u->u.getId().equals(p.getSellerId())).findFirst().orElse(users.get(0));
            String[] msgs = convos[i];
            LocalDateTime t = now.minusDays(rnd.nextInt(7)+1);
            for (int j=0; j<msgs.length; j++) {
                ChatMessage m = new ChatMessage();
                m.setProductId(p.getId());
                m.setSenderId(j%2==0?buyer.getId():seller.getId());
                m.setReceiverId(j%2==0?seller.getId():buyer.getId());
                m.setContent(msgs[j]); m.setIsRead(true);
                m.setCreatedAt(t.plusMinutes(j*3+rnd.nextInt(5)));
                chatMessageRepo.save(m);
            }
        }
    }
}
