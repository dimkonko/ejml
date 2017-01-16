/*
 * Copyright (c) 2009-2017, Peter Abeles. All Rights Reserved.
 *
 * This file is part of Efficient Java Matrix Library (EJML).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ejml.data;

/**
 * <p>Compressed Column (CC) sparse matrix format.   Only non-zero elements are stored.</p>
 * <p>
 * Format:<br>
 * Row indexes for column j are stored in rol_idx[col_idx[j]] to rol_idx[col_idx[j+1]-1].  The values
 * for the corresponding elements are stored at data[col_idx[j]] to data[col_idx[j+1]-1].<br>
 * <br>
 * Row indexes must be specified in chronological order.
 * </p>
 *
 *
 * TODO fully describe
 *
 * @author Peter Abeles
 */
public class SMatrixCmpC_F64 implements SMatrix_F64 {
    /**
     * Storage for non-zero values.  Only valid up to length-1.
     */
    public double nz_values[];
    /**
     * Length of data. Number of non-zero values in the matrix
     */
    public int nz_length;
    /**
     * Specifies which row a specific non-zero value corresponds to.
     */
    public int nz_rows[];
    /**
     * Stores the range of indexes in the non-zero lists that belong to each column.  Column 'i' corresponds to
     * indexes col_idx[i] to col_idx[i+1]-1, inclusive.
     */
    public int col_idx[];

    public int numRows;
    public int numCols;

    public SMatrixCmpC_F64(int numRows , int numCols , int nz_length) {
        nz_length = Math.min(numCols*numRows, nz_length);

        this.numRows = numRows;
        this.numCols = numCols;
        this.nz_length = nz_length;

        nz_values = new double[nz_length];
        col_idx = new int[ numCols+1 ];
        nz_rows = new int[nz_length];
    }

    public SMatrixCmpC_F64(SMatrixCmpC_F64 original ) {
        this(original.numRows, original.numCols, original.nz_length);

        set(original);
    }

    @Override
    public int getNumRows() {
        return numRows;
    }

    @Override
    public int getNumCols() {
        return numCols;
    }

    @Override
    public <T extends Matrix> T copy() {
        return (T)new SMatrixCmpC_F64(this);
    }

    @Override
    public <T extends Matrix> T createLike() {
        return (T)new SMatrixCmpC_F64(numRows,numCols, nz_length);
    }

    @Override
    public void set(Matrix original) {
        SMatrixCmpC_F64 o = (SMatrixCmpC_F64)original;
        reshape(o.numRows, o.numCols, o.nz_length);

        System.arraycopy(o.nz_values, 0, nz_values, 0, nz_length);
        System.arraycopy(o.nz_rows, 0, nz_rows, 0, nz_length);
        System.arraycopy(o.col_idx, 0, col_idx, 0, numCols);
    }

    @Override
    public void print() {
        System.out.println(getClass().getSimpleName()+" , numRows = "+numRows+" , numCols = "+numCols
                +" , nz_length = "+ nz_length);
        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols; col++) {
                int index = nz_index(row,col);
                if( index >= 0 )
                    System.out.printf("%6.3f",get(row,col));
                else
                    System.out.print("   *  ");
                if( col != numCols-1 )
                    System.out.print(" ");
            }
            System.out.println();
        }
    }

    @Override
    public void printNonZero() {
        System.out.println(getClass().getSimpleName()+" , numRows = "+numRows+" , numCols = "+numCols
                +" , nz_length = "+ nz_length);

        for (int col = 0; col < numCols; col++) {
            int idx0 = col_idx[col];
            int idx1 = col_idx[col+1];

            for (int i = idx0; i < idx1; i++) {
                int row = nz_rows[i];
                double value = nz_values[i];

                System.out.printf("%d %d %f\n",row,col,value);
            }
        }
    }

    @Override
    public double get(int row, int col) {
        if( row < 0 || row >= numRows || col < 0 || col >= numCols )
            throw new IllegalArgumentException("Outside of matrix bounds");

        return unsafe_get(row,col);
    }

    @Override
    public double unsafe_get(int row, int col) {
        int index = nz_index(row,col);
        if( index >= 0 )
            return nz_values[index];
        return 0;
    }

    public int nz_index( int row , int col ) {
        int col0 = col_idx[col];
        int col1 = col_idx[col+1];

        for (int i = col0; i < col1; i++) {
            if( nz_rows[i] == row ) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void set(int row, int col, double val) {
        if( row < 0 || row >= numRows || col < 0 || col >= numCols )
            throw new IllegalArgumentException("Outside of matrix bounds");

        unsafe_set(row,col,val);
    }

    @Override
    public void unsafe_set(int row, int col, double val) {
        int idx0 = col_idx[col];
        int idx1 = col_idx[col+1];

        for (int i = idx0; i < idx1; i++) {
            if( nz_rows[i] == row ) {
                nz_values[i] = val;
                return;
            }
        }

        throw new IllegalArgumentException("Setting of zero elements is not currently supported");
    }

    @Override
    public int getNumElements() {
        return nz_length;
    }

    @Override
    public void reshape( int numRows , int numCols , int nz_length ) {
        this.numRows = numRows;
        this.numCols = numCols;
        growMaxLength( nz_length , false);
        this.nz_length = Math.min(nz_values.length,nz_length);

        if( numCols+1 > col_idx.length ) {
            col_idx = new int[ numCols+1 ];
        }
    }

    /**
     * Increases the maximum size of the data array so that it can store sparse data up to 'length'.
     *
     * @param nz_values Desired maximum length of sparse data
     * @param preserveValue If true the old values will be copied into the new arrays.  If false that step will be skipped.
     */
    public void growMaxLength( int nz_values , boolean preserveValue ) {
        // don't increase the size beyound the max possible matrix size
        nz_values = Math.min(numRows*numCols, nz_values);
        if( nz_values > this.nz_values.length ) {
            double[] data = new double[ nz_values ];
            int[] row_idx = new int[ nz_values ];

            if( preserveValue ) {
                System.arraycopy(this.nz_values, 0, data, 0, this.nz_length);
                System.arraycopy(this.nz_rows, 0, row_idx, 0, this.nz_length);
            }

            this.nz_values = data;
            this.nz_rows = row_idx;
        }
    }

    /**
     * Checks the contract that row elements will be specified in chronomical order
     * @return true if in order or false if invalid
     */
    public boolean isRowOrderValid() {
        for (int j = 0; j < numCols; j++) {
            int idx0 = col_idx[j];
            int idx1 = col_idx[j+1];

            if( idx0 != idx1 && nz_rows[idx0] >= numRows )
                return false;

            for (int i = idx0+1; i < idx1; i++) {
                int row = nz_rows[i];
                if( nz_rows[i-1] >= row)
                    return false;
                if( row >= numRows )
                    return false;
            }
        }
        return true;
    }

    public void copyStructure( SMatrixCmpC_F64 orig ) {
        reshape(orig.numRows, orig.numCols, orig.nz_length);
        System.arraycopy(orig.col_idx,0,col_idx,0,orig.numCols+1);
        System.arraycopy(orig.nz_rows,0,nz_rows,0,orig.nz_length);
    }

    public boolean isFull() {
        return nz_length == numRows*numCols;
    }
}