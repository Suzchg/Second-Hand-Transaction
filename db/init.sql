-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- Host: localhost    Database: secondhand
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `after_sale_requests`
--

DROP TABLE IF EXISTS `after_sale_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `after_sale_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `arbitration_result` text,
  `buyer_evidence` text,
  `buyer_id` bigint DEFAULT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `deadline_at` datetime(6) DEFAULT NULL,
  `handled_at` datetime(6) DEFAULT NULL,
  `order_completed_at` datetime(6) DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `reason` text,
  `refund_amount_cent` int DEFAULT NULL,
  `refunded_at` datetime(6) DEFAULT NULL,
  `request_type` enum('PARTIAL_REFUND','REFUND_NOT_SHIPPED','REFUND_RECEIVED','RETURN_REFUND') NOT NULL,
  `requested_at` datetime(6) DEFAULT NULL,
  `responsibility` varchar(16) DEFAULT NULL,
  `return_carrier_code` varchar(32) DEFAULT NULL,
  `return_tracking_no` varchar(64) DEFAULT NULL,
  `returned_at` datetime(6) DEFAULT NULL,
  `seller_evidence` text,
  `seller_id` bigint DEFAULT NULL,
  `seller_response` text,
  `shipping_cost_cent` int DEFAULT NULL,
  `shipping_paid_by` varchar(16) DEFAULT NULL,
  `status` enum('APPROVED','CLOSED','PLATFORM_ARBITRATION','REFUNDED','REJECTED','REQUESTED','RETURN_CONFIRMED','RETURN_SHIPPED') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `after_sale_requests`
--

LOCK TABLES `after_sale_requests` WRITE;
/*!40000 ALTER TABLE `after_sale_requests` DISABLE KEYS */;
INSERT INTO `after_sale_requests` VALUES (1,NULL,NULL,3,NULL,'2026-06-11 17:46:09.139021','2026-06-14 17:46:09.139021','2026-06-11 17:47:07.037816','2026-06-11 17:45:39.500748',1,'111',888888800,'2026-06-11 17:47:07.037816','REFUND_NOT_SHIPPED','2026-06-11 17:46:09.139021',NULL,NULL,NULL,NULL,NULL,2,NULL,NULL,NULL,'REFUNDED','2026-06-11 17:47:07.037816'),(2,NULL,NULL,2,NULL,'2026-06-11 19:16:11.424963','2026-06-14 19:16:11.424963',NULL,'2026-06-11 17:46:44.652812',2,'111',99999900,NULL,'REFUND_NOT_SHIPPED','2026-06-11 19:16:11.424963',NULL,NULL,NULL,NULL,NULL,3,NULL,NULL,NULL,'REQUESTED','2026-06-11 19:16:11.424963');
/*!40000 ALTER TABLE `after_sale_requests` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `categories`
--

DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `icon_url` varchar(512) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `sort_order` int DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categories`
--

LOCK TABLES `categories` WRITE;
/*!40000 ALTER TABLE `categories` DISABLE KEYS */;
INSERT INTO `categories` VALUES (1,'2026-06-08 13:01:50.888107','📱','手机通讯',NULL,1,'2026-06-08 13:01:50.888107'),(2,'2026-06-08 13:01:50.888107','💻','电脑办公',NULL,2,'2026-06-08 13:01:50.888107'),(3,'2026-06-08 13:01:50.888107','📷','数码影音',NULL,3,'2026-06-08 13:01:50.888107'),(4,'2026-06-08 13:01:50.888107','🔌','家用电器',NULL,4,'2026-06-08 13:01:50.888107'),(5,'2026-06-08 13:01:50.888107','🛋️','家具家装',NULL,5,'2026-06-08 13:01:50.888107'),(6,'2026-06-08 13:01:50.888107','🏠','家居日用',NULL,6,'2026-06-08 13:01:50.888107'),(7,'2026-06-08 13:01:50.888107','👔','男装',NULL,7,'2026-06-08 13:01:50.888107'),(8,'2026-06-08 13:01:50.888107','👗','女装',NULL,8,'2026-06-08 13:01:50.888107'),(9,'2026-06-08 13:01:50.888107','👟','鞋靴箱包',NULL,9,'2026-06-08 13:01:50.888107'),(10,'2026-06-08 13:01:50.888107','💍','珠宝配饰',NULL,10,'2026-06-08 13:01:50.888107'),(11,'2026-06-08 13:01:50.888107','💄','美妆护肤',NULL,11,'2026-06-08 13:01:50.888107'),(12,'2026-06-08 13:01:50.888107','👶','母婴亲子',NULL,12,'2026-06-08 13:01:50.888107'),(13,'2026-06-08 13:01:50.888107','⚽','运动户外',NULL,13,'2026-06-08 13:01:50.888107'),(14,'2026-06-08 13:01:50.888107','📚','图书音像',NULL,14,'2026-06-08 13:01:50.888107'),(15,'2026-06-08 13:01:50.888107','🍎','食品生鲜',NULL,15,'2026-06-08 13:01:50.888107'),(16,'2026-06-08 13:01:50.888107','💊','医药保健',NULL,16,'2026-06-08 13:01:50.888107'),(17,'2026-06-08 13:01:50.888107','🚗','汽车用品',NULL,17,'2026-06-08 13:01:50.888107'),(18,'2026-06-08 13:01:50.888107','🐾','宠物生活',NULL,18,'2026-06-08 13:01:50.888107'),(19,'2026-06-08 13:01:50.888107','🏺','文玩收藏',NULL,19,'2026-06-08 13:01:50.888107'),(20,'2026-06-08 13:01:50.888107','🎵','乐器/音乐',NULL,20,'2026-06-08 13:01:50.888107'),(21,'2026-06-08 13:01:50.888107','🎯','潮玩/模型',NULL,21,'2026-06-08 13:01:50.888107'),(22,'2026-06-08 13:01:50.888107','🎮','游戏/电竞',NULL,22,'2026-06-08 13:01:50.888107'),(23,'2026-06-08 13:01:50.888107','🎫','票券/其他',NULL,23,'2026-06-08 13:01:50.888107');
/*!40000 ALTER TABLE `categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `chat_messages`
--

DROP TABLE IF EXISTS `chat_messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `is_read` bit(1) NOT NULL,
  `product_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `chat_messages`
--

LOCK TABLES `chat_messages` WRITE;
/*!40000 ALTER TABLE `chat_messages` DISABLE KEYS */;
INSERT INTO `chat_messages` VALUES (1,'111','2026-06-11 17:17:00.279485',_binary '',2,3,2),(2,'ok','2026-06-11 17:45:29.651762',_binary '',2,2,3);
/*!40000 ALTER TABLE `chat_messages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `comments`
--

DROP TABLE IF EXISTS `comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `comments`
--

LOCK TABLES `comments` WRITE;
/*!40000 ALTER TABLE `comments` DISABLE KEYS */;
INSERT INTO `comments` VALUES (1,'1111','2026-06-08 13:04:59.583704',1,3);
/*!40000 ALTER TABLE `comments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `favorites`
--

DROP TABLE IF EXISTS `favorites`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `favorites` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgh1s14hhb9qb8p2do933hscsf` (`user_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `favorites`
--

LOCK TABLES `favorites` WRITE;
/*!40000 ALTER TABLE `favorites` DISABLE KEYS */;
/*!40000 ALTER TABLE `favorites` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `offers`
--

DROP TABLE IF EXISTS `offers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `offers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `buyer_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `message` varchar(500) DEFAULT NULL,
  `offered_price_cent` int NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `status` enum('ACCEPTED','CANCELLED','PENDING','REJECTED') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `offers`
--

LOCK TABLES `offers` WRITE;
/*!40000 ALTER TABLE `offers` DISABLE KEYS */;
/*!40000 ALTER TABLE `offers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_events`
--

DROP TABLE IF EXISTS `order_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `from_status` varchar(255) DEFAULT NULL,
  `note` varchar(255) DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `to_status` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_events`
--

LOCK TABLES `order_events` WRITE;
/*!40000 ALTER TABLE `order_events` DISABLE KEYS */;
INSERT INTO `order_events` VALUES (1,'2026-06-08 13:05:26.941865','NONE','订单已创建，等待支付',1,'WAIT_PAY'),(2,'2026-06-08 13:05:29.062672','WAIT_PAY','买家已支付',1,'WAIT_DELIVER'),(3,'2026-06-08 13:08:27.717486','WAIT_DELIVER','卖家已发货 (sf 124)',1,'WAIT_RECEIVE'),(4,'2026-06-11 17:42:53.206009','NONE','订单已创建，等待支付',2,'WAIT_PAY'),(5,'2026-06-11 17:42:57.131719','WAIT_PAY','买家已支付',2,'WAIT_DELIVER'),(6,'2026-06-11 17:45:39.501741','WAIT_RECEIVE','买家已确认收货，资金由平台托管中（7天售后期满后自动结算给卖家）',1,'COMPLETED'),(7,'2026-06-11 17:46:21.299848','WAIT_DELIVER','卖家已发货 (sf 1234)',2,'WAIT_RECEIVE'),(8,'2026-06-11 17:46:44.652812','WAIT_RECEIVE','买家已确认收货，资金由平台托管中（7天售后期满后自动结算给卖家）',2,'COMPLETED');
/*!40000 ALTER TABLE `order_events` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_id` bigint DEFAULT NULL,
  `amount_cent` int NOT NULL,
  `buyer_id` bigint NOT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `receiver_address` varchar(255) DEFAULT NULL,
  `receiver_name` varchar(255) DEFAULT NULL,
  `receiver_phone` varchar(255) DEFAULT NULL,
  `seller_id` bigint NOT NULL,
  `settled_at` datetime(6) DEFAULT NULL,
  `shipped_at` datetime(6) DEFAULT NULL,
  `status` enum('AFTER_SALE','CANCELLED','COMPLETED','SETTLED','WAIT_DELIVER','WAIT_PAY','WAIT_RECEIVE') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (1,NULL,888888800,3,'2026-06-11 17:47:07.037816','2026-06-11 17:45:39.500748','2026-06-08 13:05:26.939867','2026-06-08 13:05:29.061672',1,'北京市 市辖区 东城区 12','12','12',2,NULL,'2026-06-08 13:08:27.714490','CANCELLED','2026-06-11 17:47:07.037816'),(2,NULL,99999900,2,NULL,'2026-06-11 17:46:44.652812','2026-06-11 17:42:53.204500','2026-06-11 17:42:57.131719',2,'北京市 市辖区 海淀区 北京航空航天大学','奶农','114514',3,NULL,'2026-06-11 17:46:21.296849','AFTER_SALE','2026-06-11 19:16:11.424963');
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_images`
--

DROP TABLE IF EXISTS `product_images`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `sort_order` int DEFAULT NULL,
  `thumbnail_url` varchar(512) NOT NULL,
  `url` varchar(512) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_images`
--

LOCK TABLES `product_images` WRITE;
/*!40000 ALTER TABLE `product_images` DISABLE KEYS */;
INSERT INTO `product_images` VALUES (1,'2026-06-08 13:03:27.599285',1,0,'/uploads/products/1/f8728a11-88f_thumb.png','/uploads/products/1/f8728a11-88f.png');
/*!40000 ALTER TABLE `product_images` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint DEFAULT NULL,
  `product_condition` enum('EIGHT_TENTHS','LIKE_NEW','NEW','NINE_TENTHS','SEVEN_TENTHS','SIX_TENTHS_AND_BELOW') DEFAULT NULL,
  `cover_image_url` varchar(512) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` text,
  `free_shipping` tinyint(1) NOT NULL DEFAULT '0',
  `price_cent` int NOT NULL,
  `quantity` int NOT NULL,
  `seller_id` bigint NOT NULL,
  `shipping_fee_cent` int DEFAULT NULL,
  `status` enum('DRAFT','OFF_SALE','ON_SALE') NOT NULL,
  `title` varchar(100) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `products`
--

LOCK TABLES `products` WRITE;
/*!40000 ALTER TABLE `products` DISABLE KEYS */;
INSERT INTO `products` VALUES (1,1,NULL,'/uploads/products/1/f8728a11-88f_thumb.png','2026-06-08 13:03:22.667668','2324',1,888888800,0,2,0,'OFF_SALE','1234','2026-06-08 13:05:26.939867'),(2,1,NULL,'','2026-06-08 13:06:45.231648','234',1,99999900,0,3,0,'OFF_SALE','34535','2026-06-11 17:42:53.204500');
/*!40000 ALTER TABLE `products` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ratings`
--

DROP TABLE IF EXISTS `ratings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ratings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `reviewer_id` bigint NOT NULL,
  `score` int NOT NULL,
  `seller_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4b8f5fs6fguy7a8ygpimjelms` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ratings`
--

LOCK TABLES `ratings` WRITE;
/*!40000 ALTER TABLE `ratings` DISABLE KEYS */;
INSERT INTO `ratings` VALUES (1,'nb','2026-06-11 17:45:49.893936',1,1,3,5,2);
/*!40000 ALTER TABLE `ratings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reports`
--

DROP TABLE IF EXISTS `reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `description` text,
  `handle_note` text,
  `handled_at` datetime(6) DEFAULT NULL,
  `handled_by` bigint DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `reason_type` enum('COUNTERFEIT','FALSE_DESC','OTHER','PRICE_FRAUD','PRIVACY','PROHIBITED') NOT NULL,
  `reporter_id` bigint NOT NULL,
  `status` enum('DISMISSED','HANDLED','PENDING') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reports`
--

LOCK TABLES `reports` WRITE;
/*!40000 ALTER TABLE `reports` DISABLE KEYS */;
/*!40000 ALTER TABLE `reports` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shipments`
--

DROP TABLE IF EXISTS `shipments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shipments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `carrier_code` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `status` enum('CREATED','DELIVERED','IN_TRANSIT') DEFAULT NULL,
  `tracking_no` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shipments`
--

LOCK TABLES `shipments` WRITE;
/*!40000 ALTER TABLE `shipments` DISABLE KEYS */;
INSERT INTO `shipments` VALUES (1,'sf','2026-06-08 13:08:27.714490',1,'CREATED','124','2026-06-08 13:08:27.714490'),(2,'sf','2026-06-11 17:46:21.296849',2,'CREATED','1234','2026-06-11 17:46:21.296849');
/*!40000 ALTER TABLE `shipments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_addresses`
--

DROP TABLE IF EXISTS `user_addresses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_addresses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `city` varchar(30) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `detail_address` varchar(200) NOT NULL,
  `district` varchar(30) NOT NULL,
  `is_default` bit(1) DEFAULT NULL,
  `province` varchar(30) NOT NULL,
  `receiver_name` varchar(50) NOT NULL,
  `receiver_phone` varchar(20) NOT NULL,
  `tag` varchar(20) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_addresses`
--

LOCK TABLES `user_addresses` WRITE;
/*!40000 ALTER TABLE `user_addresses` DISABLE KEYS */;
INSERT INTO `user_addresses` VALUES (1,'市辖区','2026-06-08 13:05:21.276603','12','东城区',_binary '','北京市','12','12','','2026-06-08 13:05:21.276603',3),(2,'市辖区','2026-06-11 17:24:04.819007','北京航空航天大学','海淀区',_binary '','北京市','奶农','114514','家','2026-06-11 17:24:04.819007',2);
/*!40000 ALTER TABLE `user_addresses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_identities`
--

DROP TABLE IF EXISTS `user_identities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_identities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `identifier` varchar(128) NOT NULL,
  `identity_type` enum('EMAIL','PHONE') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `verified` bit(1) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhhnqu0l61unw3qwlltpithsu6` (`identity_type`,`identifier`),
  KEY `FKl8i188j5rgpteq6erbt6x1h0m` (`user_id`),
  CONSTRAINT `FKl8i188j5rgpteq6erbt6x1h0m` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_identities`
--

LOCK TABLES `user_identities` WRITE;
/*!40000 ALTER TABLE `user_identities` DISABLE KEYS */;
INSERT INTO `user_identities` VALUES (1,'2026-06-08 13:01:51.816786','13800000000','PHONE','2026-06-08 13:01:51.816786',_binary '',1),(2,'2026-06-08 13:02:54.941006','3401248245@qq.com','EMAIL','2026-06-08 13:02:54.941006',_binary '\0',2),(3,'2026-06-08 13:04:53.032667','3547461283@qq.com','EMAIL','2026-06-08 13:04:53.032667',_binary '\0',3);
/*!40000 ALTER TABLE `user_identities` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `avatar_url` varchar(512) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(128) DEFAULT NULL,
  `nickname` varchar(50) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  `status` enum('ACTIVE','DISABLED') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,NULL,'2026-06-08 13:01:51.811786',NULL,'管理员','$2a$10$bPWq0ITZZF1b9neuN5P9FOz9A/IPBEpLsn/CgHo7x2cf2P09f7ER.',NULL,'ADMIN','ACTIVE','2026-06-08 13:01:51.811786'),(2,'/uploads/avatars/avatar-2-bc30e503.jpg','2026-06-08 13:02:54.941006','3401248245@qq.com','用户111112','$2a$10$ASE7A0y1vQZtxOs.k9oynOV63HUM/hjIMqspRr1AcPjfvpiZdiBji',NULL,'USER','ACTIVE','2026-06-11 17:11:31.163806'),(3,NULL,'2026-06-08 13:04:53.032667','3547461283@qq.com','用户113','$2a$10$IwJohiVeiz8lF86LXs8gg.CX98tSEBlDhqVJubXkNg1U8Twv9f2Ca','','USER','ACTIVE','2026-06-08 13:08:05.776307');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-25 11:05:06
