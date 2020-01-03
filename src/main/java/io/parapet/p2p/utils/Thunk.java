package io.parapet.p2p.utils;

public interface Thunk<T> {
    T run() throws Exception;
}