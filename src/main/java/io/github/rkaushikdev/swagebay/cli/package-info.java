/**
 * The command line, which is what the office actually uses at three in the morning.
 *
 * <p>{@link io.github.rkaushikdev.swagebay.cli.Main} is the root command and the entry point. Each
 * group of commands is a class of its own holding one nested class per command, and every command
 * prints to the {@link io.github.rkaushikdev.swagebay.cli.Terminal} it was run against, works in one
 * unit of work on the database the root names, and answers its exit status.
 */
package io.github.rkaushikdev.swagebay.cli;
