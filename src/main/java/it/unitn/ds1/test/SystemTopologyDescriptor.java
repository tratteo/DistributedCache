package it.unitn.ds1.test;

public class SystemTopologyDescriptor {
    public final int n_clients;
    public final int n_l1;
    public final int n_l2_l1;

    public SystemTopologyDescriptor(int n_clients, int n_l1, int n_l2_l1) {
        this.n_clients = n_clients;
        this.n_l1 = n_l1;
        this.n_l2_l1 = n_l2_l1;
    }
}
