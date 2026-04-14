# e4s-cache: Design Document (v1.1 - Reconstructed)

## 1. Project Overview
`e4s-cache` is a high-performance, distributed, service-oriented time-series data cache. It is designed to provide a high-availability (AP) buffer for massive-scale sensor data, acting as a high-speed read-through cache for a primary database.

## 2. Core Requirements & Scope

### 2.1 System Model
* **Deployment:** Distributed/Service-Oriented (Standalone service accessible via network).
* **Consistency Model:** AP (Availability + Partition Tolerance) - Eventual Consistency.
* **Pattern:** Read-Through (with a single source of truth in a DB).

### 2.2 Data Model
* **Structure:** Multi-dimensional Time-Series (Sensor ID + 3-week sliding window).
* **Attributes:** High-density sensor data (Voltage, Current, Power Factor, etc.).
* **Access Pattern:** Primary pattern is massive "Batch-Read" (pulling the full 3-week block for calculation).

### 2.3 Operational Logic
* **Consistency:** Eventual Consistency (AP-biased).
* **Sync Strategy:** Distributed locking/coordination to prevent "Cache Stampede" during read-through misses.
* **Eviction:** A strict, automated sliding-window (3-week TTL) to manage memory.

## 3. Proposed Architecture & Engineering Strategies

### 3.1 Storage Engine: The "Chunk-Based Columnar" Strategy
To handle a 3-week window of high-frequency data, we implement a **Chunk-Based Columnar** architecture.

* **The Concept:** Data is partitioned into **Time-Chunks** (e.t. 1-hour or 4-hour blocks).
* **Columnar Layout:** Within each chunk, attributes are stored in separate, contiguous arrays (e.g., all 'voltage' values in one array, all 'current' in another). This maximizes CPU cache hits and enables lightning-fast sequential scans.
* **The "3-Week" Sliding Window:** As time progresses, the oldest chunk is dropped (O(1) eviction).

### 3.2 Concurrency & Coordination: The "Stampede" Shield
To protect the database during a cache-miss (the "Read-Through" event):
* **Distributed Lock-on-Miss:** When a node detects a cache-miss for a specific `sensor_id`, it must acquire a distributed lock (via Redis or internal consensus) before fetching from the DB.
* **The "Winner" Strategy:** The node that acquires the lock performs the "fetch-and-prime" operation. Other nodes wait briefly and then perform a "Read-Only" check.

### 3.3 Memory Management: The "Direct-Buffer" Strategy
To avoid Java's Garbage Collection (GC) latency spikes, we use **Off-Heap Memory Management**.
* **The Strategy:** Instead of storing objects on the JVM heap, we use `java.nio.ByteBuffer` (Direct Buffers) to store the raw, columnar data.
* **The "3-Week" Sliding Window:** As time progresses, the oldest chunk is dropped (O(1) eviction).

## 4. API & Communication Spec

### 4.1 API Contract
* **Primary Interface:** gRPC/Protobuf for high-performance service calls.
* **Key Operations:**
    * `GetSeries(sensor_id, start_time, end_time, attributes[])`
    * `FillSeries(sensor_id, data_points[])`

### 4.2 Distributed Coordination
* **Locking:** Distributed locking (e.g., Redis or internal consensus) to prevent cache stampedes.
* **Hashing:** Consistent hashing to distribute sensor IDs across the cluster.

## 5. Developer Implementation Guide (The "Audit" Criteria)

When the implementation is submitted for review, it will be evaluated against these technical constraints:

1. **Compliance with the Design Document:** Does the code actually implement the "Option B: Buffer-based" strategy and the "Chunk-columnar" architecture?
2. **Memory & Performance:** Are they correctly using `ByteBuffer` and managing offsets to avoid GC pressure? Is the "Scan" efficiency meeting our requirements?
3. **Concurrency & Safety:** How are they handling the "Write-index" and the "Distributed Locking" logic?
4. **Code Quality:** Is the code clean, type-safe, and maintainable?

## 6. Appendix: Design Clarifications Requested

Before starting implementation, clarity is needed on these:

1. **Java version**: (e.g., OpenJDK 17/21?)
2. **Chunk granularity**: (e.g., 1-hour vs 4-hour?)
3. **Memory budget**: (e.g., per-node RAM limit?)
4. **Target throughput**: (e.g., writes per second, reads per second?)
5. **Latency SLA**: (e.g., p99 latency targets?)
