package com.faendir.acra.persistence.report

import com.faendir.acra.dataprovider.AcrariumDataProvider
import com.faendir.acra.dataprovider.AcrariumSort
import com.faendir.acra.jooq.generated.Indexes
import com.faendir.acra.jooq.generated.Tables.ATTACHMENT
import com.faendir.acra.jooq.generated.Tables.REPORT
import com.faendir.acra.persistence.and
import com.faendir.acra.persistence.app.AppId
import com.faendir.acra.persistence.asOrderFields
import com.faendir.acra.persistence.bug.BugId
import com.faendir.acra.persistence.fetchList
import com.faendir.acra.persistence.fetchListInto
import com.faendir.acra.persistence.fetchValue
import com.faendir.acra.persistence.fetchValueInto
import mu.KotlinLogging
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.stream.Stream

private val logger = KotlinLogging.logger {}

@Repository
class ReportRepository(
    private val jooq: DSLContext
) {
    @PostAuthorize("returnObject == null || hasViewPermission(returnObject.appId)")
    fun find(id: String): Report? = jooq.selectFrom(REPORT).where(REPORT.ID.eq(id)).fetchValueInto()

    @PostAuthorize("returnObject == null || hasViewPermission(returnObject.appId)")
    fun findAttachmentNames(id: String) = jooq.select(ATTACHMENT.FILENAME).from(ATTACHMENT).where(ATTACHMENT.REPORT_ID.eq(id)).fetchList()

    fun loadAttachment(reportId: String, filename: String) =
        jooq.select(ATTACHMENT.CONTENT).from(ATTACHMENT).where(ATTACHMENT.REPORT_ID.eq(reportId).and(ATTACHMENT.FILENAME.eq(filename))).fetchValue()

    @PreAuthorize("hasViewPermission(#appId)")
    fun listIds(appId: AppId, after: Instant?, before: Instant?): List<String> =
        jooq.select(REPORT.ID)
            .from(REPORT)
            .where(REPORT.APP_ID.eq(appId), *listOfNotNull(after?.let { REPORT.DATE.gt(it) }, before?.let { REPORT.DATE.lt(it) }).toTypedArray())
            .fetchList()

    @PreAuthorize("hasViewPermission(#appId)")
    fun <T> get(appId: AppId, field: Field<T>, where: Condition? = null, sorted: Boolean = true): List<T> =
        jooq.selectDistinct(field).from(REPORT).where(REPORT.APP_ID.eq(appId), *listOfNotNull(where).toTypedArray()).run { if (sorted) orderBy(field) else this }.fetchList()

    @PreAuthorize("hasViewPermission(#appId)")
    fun <T> countGroupedBy(appId: AppId, field: Field<T>, where: Condition?): Map<T, Int> =
        jooq.select(field, DSL.count(REPORT.ID))
            .from(REPORT)
            .where(REPORT.APP_ID.eq(appId), *listOfNotNull(where).toTypedArray())
            .groupBy(field)
            .associate { it[field] to it[DSL.count(REPORT.ID)] }


    @Transactional
    @PreAuthorize("isReporter()")
    fun create(report: Report, attachments: Map<String, ByteArray>) {
        jooq.insertInto(REPORT)
            .set(jooq.newRecord(REPORT, report))
            .execute()
        for ((name, content) in attachments) {
            try {
                jooq.insertInto(ATTACHMENT)
                    .set(ATTACHMENT.REPORT_ID, report.id)
                    .set(ATTACHMENT.FILENAME, name)
                    .set(ATTACHMENT.CONTENT, content)
                    .execute()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to upload attachment $name for report ${report.id}" }
            }
        }
    }

    @Transactional
    @PreAuthorize("hasEditPermission(#appId)")
    fun delete(appId: AppId, reportId: String) {
        jooq.deleteFrom(REPORT).where(REPORT.ID.eq(reportId)).execute()
    }

    @Transactional
    @PreAuthorize("hasEditPermission(#appId)")
    fun deleteBefore(appId: AppId, instant: Instant) {
        jooq.deleteFrom(REPORT).where(REPORT.DATE.lt(instant)).execute()
    }

    @Transactional
    @PreAuthorize("hasEditPermission(#appId)")
    fun deleteBefore(appId: AppId, versionCode: Int) {
        jooq.deleteFrom(REPORT).where(REPORT.VERSION_CODE.lt(versionCode)).execute()
    }

    fun countInRange(appId: AppId, bugId: BugId, range: ClosedRange<Instant>): Int = jooq.selectCount().from(REPORT)
        .where(REPORT.APP_ID.eq(appId), REPORT.BUG_ID.eq(bugId), REPORT.DATE.greaterOrEqual(range.start), REPORT.DATE.lessOrEqual(range.endInclusive)).fetchValue() ?: 0

    @PreAuthorize("hasViewPermission(#appId)")
    fun getProvider(appId: AppId, customColumns: List<String>) = getProvider(REPORT.APP_ID.eq(appId), customColumns)

    @PreAuthorize("hasViewPermission(#appId)")
    fun getProvider(appId: AppId, bugId: BugId, customColumns: List<String>) = getProvider(REPORT.BUG_ID.eq(bugId), customColumns)

    @PreAuthorize("hasViewPermission(#appId)")
    fun getProvider(appId: AppId, installationId: String, customColumns: List<String>) = getProvider(REPORT.INSTALLATION_ID.eq(installationId), customColumns)

    private fun getProvider(condition: Condition, customColumns: List<String>) = object : AcrariumDataProvider<ReportRow, ReportRow.Filter, ReportRow.Sort>() {
        override fun fetch(filters: Set<ReportRow.Filter>, sort: List<AcrariumSort<ReportRow.Sort>>, offset: Int, limit: Int): Stream<ReportRow> {
            val customColumnFields = customColumns.mapIndexed { index, it -> DSL.jsonValue(REPORT.CONTENT, "$.$it").`as`("CUSTOM_COLUMN$index") }
            return jooq.select(
                REPORT.ID,
                REPORT.ANDROID_VERSION,
                REPORT.DATE,
                REPORT.PHONE_MODEL,
                REPORT.MARKETING_DEVICE,
                REPORT.INSTALLATION_ID,
                REPORT.IS_SILENT,
                REPORT.EXCEPTION_CLASS,
                REPORT.MESSAGE,
                REPORT.VERSION_CODE,
                REPORT.VERSION_FLAVOR,
                REPORT.BUG_ID,
                *customColumnFields.toTypedArray()
            )
                .from(REPORT.useIndex(*sort.mapNotNull { it.sort.index }.map { it.name }.toTypedArray()))
                .where(condition.and(filters))
                .orderBy(sort.asOrderFields())
                .offset(offset)
                .limit(limit)
                .map { record ->
                    ReportRow(
                        id = record[REPORT.ID],
                        androidVersion = record[REPORT.ANDROID_VERSION],
                        date = record[REPORT.DATE],
                        phoneModel = record[REPORT.PHONE_MODEL],
                        marketingDevice = record[REPORT.MARKETING_DEVICE],
                        installationId = record[REPORT.INSTALLATION_ID],
                        isSilent = record[REPORT.IS_SILENT],
                        exceptionClass = record[REPORT.EXCEPTION_CLASS],
                        message = record[REPORT.MESSAGE],
                        versionCode = record[REPORT.VERSION_CODE],
                        versionFlavor = record[REPORT.VERSION_FLAVOR],
                        bugId = record[REPORT.BUG_ID],
                        customColumns = customColumnFields.map { record[it]?.data() }
                    )
                }
                .stream()
        }

        override fun size(filters: Set<ReportRow.Filter>) = jooq.selectCount().from(REPORT).where(condition.and(filters)).fetchValue() ?: 0
    }

    @PreAuthorize("hasViewPermission(#appId)")
    fun getInstallationProvider(appId: AppId) = object : AcrariumDataProvider<Installation, Installation.Filter, Installation.Sort>() {
        override fun fetch(filters: Set<Installation.Filter>, sort: List<AcrariumSort<Installation.Sort>>, offset: Int, limit: Int) = jooq.select(
            REPORT.INSTALLATION_ID,
            DSL.count(REPORT.ID).`as`("REPORT_COUNT"),
            DSL.max(REPORT.DATE).`as`("LATEST_REPORT"),
        ).from(REPORT.useIndex(Indexes.REPORT_IDX_REPORT_INSTALLATION_ID.name))
            .where(REPORT.APP_ID.eq(appId))
            .orderBy(sort.asOrderFields())
            .offset(offset)
            .limit(limit)
            .fetchListInto<Installation>()
            .stream()

        override fun size(filters: Set<Installation.Filter>) = jooq.select(DSL.countDistinct(REPORT.INSTALLATION_ID)).from(REPORT).where(REPORT.APP_ID.eq(appId)).fetchValue() ?: 0

    }
}