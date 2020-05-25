package ca.uhn.fhir.jpa.packages;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.data.INpmPackageDao;
import ca.uhn.fhir.jpa.dao.data.INpmPackageVersionDao;
import ca.uhn.fhir.jpa.model.entity.NpmPackageEntity;
import ca.uhn.fhir.jpa.model.entity.NpmPackageVersionEntity;
import ca.uhn.fhir.jpa.model.entity.NpmPackageVersionEntityPk;
import ca.uhn.fhir.jpa.model.entity.ResourceTable;
import ca.uhn.fhir.rest.api.server.storage.ResourcePersistentId;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.util.BinaryUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseBinary;
import org.hl7.fhir.utilities.cache.BasePackageCacheManager;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class JpaPackageCache extends BasePackageCacheManager {

	@Autowired
	private INpmPackageDao myPackageDao;
	@Autowired
	private INpmPackageVersionDao myPackageVersionDao;
	@Autowired
	private DaoRegistry myDaoRegistry;
	@Autowired
	private FhirContext myCtx;
	@Autowired
	private PlatformTransactionManager myTxManager;

	@Override
	protected NpmPackage loadPackageFromCacheOnly(String theId, @Nullable String theVersion) throws IOException {
		Validate.notBlank(theId, "theId must be populated");

		if (isNotBlank(theVersion)) {
			return myPackageVersionDao.findById(new NpmPackageVersionEntityPk(theId, theVersion)).map(t -> loadPackage(t)).orElse(null);
		}

		Optional<NpmPackageEntity> pkg = myPackageDao.findById(theId);
		if (pkg.isPresent()) {
			return loadPackage(theId, pkg.get().getCurrentVersionId());
		}

		return null;
	}

	private NpmPackage loadPackage(NpmPackageVersionEntity thePackageVersion) {
		IFhirResourceDao<? extends IBaseBinary> binaryDao = getBinaryDao();
		IBaseBinary binary = binaryDao.readByPid(new ResourcePersistentId(thePackageVersion.getPackageBinary().getId()));
		ByteArrayInputStream inputStream = new ByteArrayInputStream(binary.getContent());
		try {
			return NpmPackage.fromPackage(inputStream);
		} catch (IOException e) {
			throw new InternalErrorException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private IFhirResourceDao<IBaseBinary> getBinaryDao() {
		return myDaoRegistry.getResourceDao("Binary");
	}

	@Override
	public NpmPackage addPackageToCache(String thePackageId, String thePackageVersion, InputStream thePackageTgzInputStream, String theSourceDesc) throws IOException {
		byte[] bytes = IOUtils.toByteArray(thePackageTgzInputStream);

		NpmPackage npmPackage = NpmPackage.fromPackage(new ByteArrayInputStream(bytes));

		IBaseBinary binary = BinaryUtil.newBinary(myCtx);
		BinaryUtil.setData(myCtx, binary, bytes, "application/gzip");

		newTxTemplate().executeWithoutResult(t->{
			ResourceTable persistedPackage = (ResourceTable) getBinaryDao().create(binary).getEntity();
			NpmPackageEntity pkg = myPackageDao.findById(thePackageId).orElseGet(() -> createPackage(npmPackage));
			NpmPackageVersionEntity packageVersion = my
		});

		return npmPackage;
	}

	private NpmPackageEntity createPackage(NpmPackage theNpmPackage) {
		NpmPackageEntity entity = new NpmPackageEntity();
		entity.setPackageId(theNpmPackage.id());
		entity.setCurrentVersionId(theNpmPackage.version());
		return myPackageDao.save(entity);
	}

	@Override
	public NpmPackage loadPackage(String thePackageId, String thePackageVersion) throws FHIRException, IOException {
		return loadPackageFromCacheOnly(thePackageId, thePackageVersion);
	}

	private TransactionTemplate newTxTemplate() {
		return new TransactionTemplate(myTxManager);
	}
}