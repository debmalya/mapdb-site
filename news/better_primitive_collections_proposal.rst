Better primitive collections
===============================

.. post:: 2015-11-20
    :tags: collections primitive
    :author: Jan

There are many primitive collections libraries for Java. Uf you are not familiar with them, read
`this overview <http://java-performance.info/hashmap-overview-jdk-fastutil-goldman-sachs-hppc-koloboke-trove-january-2015/>`_
I like them for reduced memory usage and improved performance. Primitive collections are also part of MapDB.

Recently I faced problem which could not be solved by existing library.
There is `R5 routing framework <https://github.com/conveyal/r5>`_ developed by Conveyal.
On each startup it loads data into memory, performs calculations and exits.
Data are large, and can not be stored permanently stored in-memory.
Also memory usage is already optimized, so there is no way to improve it.

In this situation primitive collections (Trove in this case) are failing because they do not fit onto memory.
Databases (graph or MapDB) can handle big data, but will fail due to performance overhead.
R5 is quite optimized and regular database has too big latencies and can not compete with ``long[]`` arrays.

Memory mapped files
----------------------

So there is need for new primitive collection. It should store data in-memory,
but also be swappable to disk if free memory runs low. On-heap ``long[]`` is not really swappable,
Garbage Collector might randomly touch an array and load it from swap.
Also data should be persisted between JVM restarts to save import time (without crash protection).
That sounds like a job for memory mapped files.

Bit of theory: Collections such as HashSet<Long> store data in an array: ``Long[]``.
Primitive collections remove boxing: ``long[]``.
But one could also use raw binary storage such as ``byte[]``, ``DirectByteByffer``,
``RandomAccessFile`` or ``MappedByteBuffer``.
Access to ``longs`` will get slightly complicated, but most
storage options provide methods such as ``setLong(index,value)`` or ``getLong(index)``
and are very similar to arrays.

It seems fairly straightforward, but things get complicated very quickly with resize, initialization, shutdown etc.
From MapDB I have some experience with using alternative storage back-ends (MapDB supports about 10 memory and file backends),
so I will give it a try.

Code generators
------------------

Primitive collections relay on code generators to combine various types together. There are many classes
such as ``IntObjectHashMap<V>`` or ``IntLongHashMap<V>``. So it should be possible to
modify code generator and add one extra parameters for storage. Than there will be classes such as
``MmapIntObjectHashMap<V>`` or ``HeapIntLongHashMap<V>``.

Large number of classes expands Jar files. Koloboke and FastUtil both have binary jar files with 16MB size.
Extra combinations for backend will bring jar file size to around 100MB. I think it is reasonable
to split implementations into several (hundreds?) smaller jar files. MapDB already has scripts to
deploy autogenerated artifacts into Maven repositories.

It is question what primitive collection library should be extended/forked to do this. I am leaning towards
`Koloboke <https://github.com/OpenHFT/Koloboke>`_, because its code is already used in MapDB and
`their proposal <https://github.com/OpenHFT/Koloboke/wiki/Koloboke:-roll-the-collection-implementation-with-features-you-need>`_
is similar to my own plans.


Byte versus 8bit Integer
-------------------------

Most primitive collections supports less common primitive types: (boolean, byte, char and float).
I think their only benefit is reduced storage space.
So we can remove of less common primitive types, if bigger types will optionally consume smaller space in storage.
``CharSet`` has no advantage over ``IntSet`` if integers are only read from 16 bytes.

Also Java8 streams have basic support for primitive collections. There is ``Stream`` class, but also
specialized ``IntStream``, ``LongStream`` and ``DoubleStream``. Other primitive types are missing.

So I think it makes a sense to only keep ``int``, ``long`` and ``double``. Other primitive types will be removed.

As alternative one will be able to specify how many bites each entry consumes.
So one can have ``LongLongMap`` where key consumes 48 bits and value 56 bits (one case from MapDB).
It will be code generated, so there will be classes such as ``Long6Long7HashMap``.

64bit support
--------------------

Java is 32bit by design: ``Object.hashCode()``, maximal array size, ``List.get(int)``, ``ByteBuffer`` addressing,
``Collection.size()`` etc..
I find it quite frustrating, so this collection framework should offer both 32bit and 64bit options.
It should support 64bit hashCode, maximal sizes,  List getters and so on. Some
collections in memory-mapped files could easily have terabytes of data and 10^12 elements (real case from MapDB).

Data corruption
-----------------

If collection contains gigabytes (or terrabytes) of data, it probably took very long time to create.
So there should be some sort of persistence between JVM restarts.
It is already done if collection is stored in memory mapped file.

Working on MapDB taught me that there must be at least some protection in
case JVM crashed and file become corrupted.
Corrupted files could introduce all sort of false alarms and strange bugs.

So this library will have two options to detect data corruption. First option is file seal based on timestamp.
It detects if file was closed correctly or JVM somehow crashed before closing it.
It is good enough for most cases and has almost no overhead.

Another option to detect file corruption is file checksum. That requires file traversal and checksum calculation
every time file is opened or closed.

Object serialization
-----------------------

Data are stored in binary form. MapDB has fast and space efficient serialization framework,
that will be used for non-primitive data. Performance overhead of deserialization in MapDB is around 20%.

For better performance there will be an option to skip serialization, and use data in serialized form.
In this case operations such as Map.get() return only file offset and user will use library such as
Protobuf or Thrift to interpret binary data.

Fixed and variable sizes
-------------------------

Array like structures can only work with fixed size records.
For variable sized records collection can use ``StoreDirect`` class from MapDB.
It operates over mmap file and provides space allocator, compaction, free space reuse etc..

Collection such as ``HashMap<long,Object>`` would combine two files: an array of longs for hash table, and second file
for values which managed by ``StoreDirect``.


