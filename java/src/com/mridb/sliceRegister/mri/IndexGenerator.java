package com.mridb.sliceRegister.mri;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.BaseStream;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class IndexGenerator  {
	
	long maxIndex;
	LongStream longStream;
	
	public IndexGenerator(long maxIndex) {
		this.maxIndex = maxIndex;
	}
	public LongStream getStream() {
		if(this.longStream != null) {
			this.longStream.close();
		}
		this.longStream = LongStream.range(0, maxIndex).parallel();
		return this.longStream;
	}
	
	public static void main(String[] args) {
		IndexGenerator gen = new IndexGenerator(10);
		boolean isParallel = gen.longStream.isParallel();
		gen.longStream.forEach(System.out::println);
		int dummy = 1;
	}
	
//	public synchronized long next() {
//		
//		long result = currentIndex;
//		currentIndex++;
//		return result;
//	}

}
