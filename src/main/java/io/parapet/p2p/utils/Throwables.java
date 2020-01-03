package io.parapet.p2p.utils;

public final class Throwables {

    private Throwables() {

    }


    public static void suppressError(VoidThunk s) {
        try {
            s.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
