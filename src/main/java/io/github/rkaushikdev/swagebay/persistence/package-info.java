/**
 * The database: the tables, the migrations that build them, the unit of work, and the repositories
 * that are the only place a query is written.
 *
 * <p>A repository is not an abstraction over the database. It is a place to put the queries so that
 * they are all in one file and can be read together, and so that a query written for one caller
 * gets reused by the next one instead of being written slightly differently.
 *
 * <p>Nothing here opens or closes a transaction. A repository is handed the entity manager of a
 * {@link io.github.rkaushikdev.swagebay.persistence.UnitOfWork} and uses it, and whoever owns the
 * unit decides when to commit. A repository that commits is a repository that cannot be used twice
 * in one unit of work, which is discovered late and hurts.
 *
 * <p>Every finder that can miss returns an {@code Optional} and every finder that must not miss
 * throws {@link io.github.rkaushikdev.swagebay.NotFoundError} with the identity in it. Mixing the
 * two, so that one method returns nothing and another throws, is the thing that produces a caller
 * full of defensive checks in the places that do not need them and none in the places that do.
 */
package io.github.rkaushikdev.swagebay.persistence;
