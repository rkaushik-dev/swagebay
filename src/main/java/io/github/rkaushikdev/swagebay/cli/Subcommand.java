package io.github.rkaushikdev.swagebay.cli;

import java.util.concurrent.Callable;

/** A command whose work answers an exit status and never fails with a checked exception. */
interface Subcommand extends Callable<Integer> {

    @Override
    Integer call();
}
