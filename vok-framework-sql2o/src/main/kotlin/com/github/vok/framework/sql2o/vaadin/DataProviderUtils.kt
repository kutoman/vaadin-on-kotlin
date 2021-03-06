package com.github.vok.framework.sql2o.vaadin

import com.github.vokorm.Filter
import com.github.vokorm.SqlWhereBuilder
import com.github.vokorm.and
import com.vaadin.data.provider.ConfigurableFilterDataProvider
import com.vaadin.data.provider.DataProvider
import com.vaadin.data.provider.Query
import com.vaadin.data.provider.QuerySortOrder
import com.vaadin.server.SerializablePredicate
import java.util.stream.Stream

/**
 * A data provider with configurable filter - use [setFilter] to set your custom filter. Note that user-provided filter value in Grid
 * will overwrite this filter; if you want an unremovable filter use [withFilter].
 */
typealias VokDataProvider<T> = ConfigurableFilterDataProvider<T, Filter<T>?, Filter<T>?>

/**
 * Wraps this data provider in a configurable filter, regardless of whether this data provider is already a configurable filter or not.
 * @return a new data provider which can be outfitted with a custom filter.
 */
fun <T: Any> DataProvider<T, in Filter<T>?>.withConfigurableFilter2() : VokDataProvider<T> =
    withConfigurableFilter({ f1: Filter<T>?, f2: Filter<T>? -> listOfNotNull(f1, f2).toSet().and() })

/**
 * Produces a new data provider which always applies given [other] filter and restricts rows returned by the original data provider to given filter.
 *
 * Invoking this method multiple times will chain the data providers and restrict the rows further.
 * @param other applies this filter
 * @return a [VokDataProvider]; setting the [ConfigurableFilterDataProvider.setFilter] won't overwrite the filter specified in this method.
 */
fun <T: Any> DataProvider<T, in Filter<T>?>.withFilter(other: Filter<T>) : VokDataProvider<T> =
    withConfigurableFilter2().apply {
        // wrap the current DP so that we won't change the filter
        setFilter(other)
        // wrap the DP again so that nobody will change our filter.
    }.withConfigurableFilter2()

// since SerializablePredicate is Vaadin-built-in interface which our Filters do not use, since Vaadin 8 has different SerializablePredicate class
// than Vaadin 10 and we would have to duplicate filters just because a dumb interface.
fun <T: Any> DataProvider<T, in SerializablePredicate<T>?>.withVoKFilterAdapter() : VokDataProvider<T> =
    withConvertedFilter<Filter<T>?> { f ->
        if (f == null) null else SerializablePredicate { f.test(it) }
    }.withConfigurableFilter2()

/**
 * Produces a new data provider with unremovable [filter] which restricts rows returned by the receiver data provider.
 *
 * Invoking this method multiple times will chain the data providers and restrict the rows further.
 * @return a [VokDataProvider]; setting the [ConfigurableFilterDataProvider.setFilter] won't overwrite the filter specified in this method.
 */
@JvmName("withFilterAndConvert")
fun <T: Any> DataProvider<T, in SerializablePredicate<T>?>.withFilter(filter: Filter<T>) : VokDataProvider<T> =
    withVoKFilterAdapter().withFilter(filter)

/**
 * Produces a new data provider with unremovable filter which restricts rows returned by the receiver data provider.
 * Allows you to write
 * expressions like this: `Person.dataProvider.withFilter { Person::age lt 25 }`
 * See [SqlWhereBuilder] for a complete list of applicable operators.
 *
 * Invoking this method multiple times will restrict the rows further.
 * @param block the block which allows you to build the `where` expression.
 * @return a [VokDataProvider]; setting the [ConfigurableFilterDataProvider.setFilter] won't overwrite the filter specified in this method.
 */
fun <T: Any> DataProvider<T, in Filter<T>?>.withFilter(block: SqlWhereBuilder<T>.()-> Filter<T>) : VokDataProvider<T> =
    withFilter(block(SqlWhereBuilder()))

@JvmName("withFilterVaadin2")
fun <T: Any> DataProvider<T, in SerializablePredicate<T>?>.withFilter(block: SqlWhereBuilder<T>.()-> Filter<T>) : VokDataProvider<T> =
    withVoKFilterAdapter().withFilter(block)

/**
 * Removes the original filter and sets the new filter. Allows you to write
 * expressions like this: `sqlDataProvider<Person>().setFilter { Person::age lt 25 }`.
 * See [SqlWhereBuilder] for a complete list of applicable operators.
 *
 * Invoking this method multiple times will overwrite the previous filter.
 * @param block the block which allows you to build the `where` expression.
 */
fun <T: Any> VokDataProvider<T>.setFilter(block: SqlWhereBuilder<T>.()-> Filter<T>) {
    setFilter(block(SqlWhereBuilder()))
}

/**
 * Creates a new data provider which delegates to [delegate] but always appends given list of sort orders.
 * This class can be used to specify the default ordering if the Grid is currently unsorted.
 */
class AppendSortDataProvider<T, F> (private val append: List<QuerySortOrder>, private val delegate: DataProvider<T, F>) : DataProvider<T, F> by delegate {
    init {
        require(!delegate.isInMemory) { "$delegate is in-memory which is unsupported" }
    }
    override fun fetch(query: Query<T, F>): Stream<T> {
        val sortOrders: List<QuerySortOrder> = query.sortOrders ?: listOf()
        val q = Query(query.offset, query.limit, sortOrders + append, query.inMemorySorting, query.filter.orElse(null))
        return delegate.fetch(q)
    }
}

/**
 * Returns a new data provider which delegates to receiver but with given list of [sort] orders appended to anything passed in to [com.vaadin.flow.data.provider.Query].
 * The [com.vaadin.flow.data.provider.Query.sortOrders] take precedence: any user-specified sorting in Grid takes precedence.
 *
 * This can thus be used to specify the default ordering of the data source in case when the user selected no sorting for the Grid.
 *
 * Cannot be used on in-memory data provider - this function will throw [IllegalArgumentException] if receiver is an in-memory data provider.
 *
 * Example of usage: `grid.dataProvider = Person.dataProvider.sortedBy(Person::name.asc)`
 * @receiver delegate all data fetching calls to here
 * @param sort append these sort criteria. May be empty - in that case just returns the receiver.
 */
fun <T: Any> VokDataProvider<T>.sortedBy(vararg sort: QuerySortOrder): VokDataProvider<T> = when {
    sort.isEmpty() -> this
    else -> AppendSortDataProvider(sort.toList(), this).withConfigurableFilter2()
}
