/*
 * Apfloat arbitrary precision arithmetic library
 * Copyright (C) 2002-2017  Mikko Tommila
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.apfloat.internal;

import org.apfloat.ApfloatContext;
import org.apfloat.ApfloatRuntimeException;
import org.apfloat.spi.ConvolutionStrategy;
import org.apfloat.spi.DataStorageBuilder;
import org.apfloat.spi.DataStorage;

/**
 * Medium-length convolution strategy.
 * Performs a simple O(n<sup>2</sup>) multiplication when the size of one operand is relatively short.
 *
 * @version 1.8.0
 * @author Mikko Tommila
 */

public class RawtypeMediumConvolutionStrategy
    extends RawtypeBaseMath
    implements ConvolutionStrategy
{
    // Implementation notes:
    // - Assumes that the operands have been already truncated to match resultSize (the resultSize argument is ignored)
    // - This class probably shouldn't be converted to a single class using generics because there is some performance impact

    /**
     * Creates a convolution strategy using the specified radix.
     *
     * @param radix The radix that will be used.
     */

    public RawtypeMediumConvolutionStrategy(int radix)
    {
        super(radix);
    }

    public DataStorage convolute(DataStorage x, DataStorage y, long resultSize)
        throws ApfloatRuntimeException
    {
        DataStorage shortStorage, longStorage;

        if (x.getSize() > y.getSize())
        {
            shortStorage = y;
            longStorage = x;
        }
        else
        {
            shortStorage = x;
            longStorage = y;
        }

        long shortSize = shortStorage.getSize(),
             longSize = longStorage.getSize(),
             size = shortSize + longSize;

        if (shortSize > Integer.MAX_VALUE)
        {
            throw new ApfloatInternalException("Too long shorter number, size = " + shortSize);
        }

        final int bufferSize = (int) shortSize;

        ApfloatContext ctx = ApfloatContext.getContext();
        DataStorageBuilder dataStorageBuilder = ctx.getBuilderFactory().getDataStorageBuilder();
        DataStorage resultStorage = dataStorageBuilder.createDataStorage(size * sizeof(rawtype));
        resultStorage.setSize(size);

        DataStorage.Iterator src = longStorage.iterator(DataStorage.READ, longSize, 0),
                             dst = resultStorage.iterator(DataStorage.WRITE, size, 0),
                             tmpDst = new DataStorage.Iterator()                        // Cyclic iterator
                             {
                                 @Override
                                 public void next()
                                 {
                                     this.position++;
                                     this.position = (this.position == bufferSize ? 0 : this.position);
                                 }

                                 @Override
                                 public rawtype getRawtype()
                                 {
                                     return this.buffer[this.position];
                                 }

                                 @Override
                                 public void setRawtype(rawtype value)
                                 {
                                     this.buffer[this.position] = value;
                                 }

                                 private static final long serialVersionUID = 1L;

                                 private rawtype[] buffer = new rawtype[bufferSize];
                                 private int position = 0;
                             };

        for (long i = 0; i < longSize; i++)
        {
            DataStorage.Iterator tmpSrc = shortStorage.iterator(DataStorage.READ, shortSize, 0);        // Sub-optimal: this could be cyclic also

            rawtype factor = src.getRawtype(),          // Get one word of source data
                    carry = baseMultiplyAdd(tmpSrc, tmpDst, factor, 0, tmpDst, shortSize),
                    result = tmpDst.getRawtype();       // Least significant word of the result

            dst.setRawtype(result);     // Store one word of result

            tmpDst.setRawtype(carry);   // Set carry from calculation as new last word in cyclic buffer

            tmpDst.next();              // Cycle buffer; current first word becomes last
            src.next();
            dst.next();
        }

        // Exhaust last words from temporary cyclic buffer and store them to result data
        for (int i = 0; i < bufferSize; i++)
        {
            rawtype result = tmpDst.getRawtype();
            dst.setRawtype(result);

            tmpDst.next();
            dst.next();
        }

        return resultStorage;
    }

    private static final long serialVersionUID = ${org.apfloat.internal.RawtypeMediumConvolutionStrategy.serialVersionUID};
}
