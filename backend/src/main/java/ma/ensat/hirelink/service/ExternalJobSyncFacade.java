package ma.ensat.hirelink.service;

import ma.ensat.hirelink.service.external.ExternalJobConnector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestre la synchronisation des sources activees.
 * Phase 2 : delegue aux ExternalJobConnector (Greenhouse/Lever/Ashby) quand presents ;
 * conserve Remote OK / Maroc comme chemins legacy.
 */
@Service
public class ExternalJobSyncFacade {

    private final ExternalJobImportService imports;
    private final ExternalSourceCatalog catalog;
    private final RemoteOkClient remoteOkClient;
    private final RemoteOkParser remoteOkParser = new RemoteOkParser();
    private final MarocPartnerFeedParser marocParser = new MarocPartnerFeedParser();
    private final ResourceLoader resources;
    private final String marocFeedLocation;
    private final Map<String, ExternalJobConnector> connectors = new LinkedHashMap<>();

    public ExternalJobSyncFacade(ExternalJobImportService imports,
                                 ExternalSourceCatalog catalog,
                                 RemoteOkClient remoteOkClient,
                                 ResourceLoader resources,
                                 List<ExternalJobConnector> connectorList,
                                 @Value("${hirelink.external-jobs.maroc.feed:classpath:feeds/maroc-emploi-partenaires.v1.json}") String marocFeedLocation) {
        this.imports = imports;
        this.catalog = catalog;
        this.remoteOkClient = remoteOkClient;
        this.resources = resources;
        this.marocFeedLocation = marocFeedLocation;
        if (connectorList != null) {
            for (ExternalJobConnector c : connectorList) {
                connectors.put(c.sourceCode(), c);
            }
        }
    }

    public List<ExternalJobImportService.SyncReport> syncAllEnabled() {
        List<ExternalJobImportService.SyncReport> reports = new ArrayList<>();
        for (ExternalSourceCatalog.SourceInfo source : catalog.enabled()) {
            reports.add(syncOne(source.code()));
        }
        if (reports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aucune source externe n'est activee : la synchronisation est desactivee dans la configuration.");
        }
        return reports;
    }

    public ExternalJobImportService.SyncReport syncOne(String code) {
        ExternalSourceCatalog.SourceInfo info = catalog.find(code);
        if (info == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source inconnue.");
        }
        if (!info.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La source " + info.label() + " est desactivee : aucune requete n'a ete envoyee.");
        }
        try {
            ExternalJobConnector connector = connectors.get(info.code());
            if (connector != null) {
                return imports.importSnapshot(info.code(), connector.fetchNormalized());
            }
            if (ExternalSourceCatalog.REMOTE_OK.equals(info.code())) {
                return imports.importSnapshot(info.code(), remoteOkParser.parse(remoteOkClient.fetch()));
            }
            if (ExternalSourceCatalog.MAROC_EMPLOI.equals(info.code())) {
                return imports.importSnapshot(info.code(), marocParser.parse(readFeed(marocFeedLocation)));
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connecteur non implemente pour " + info.code());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Le flux " + info.label() + " a un format inattendu. Les offres deja importees sont conservees.");
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Impossible de lire le flux " + info.label() + ".");
        }
    }

    /** Sync d'un board ATS allowliste (provider + boardKey). */
    public ExternalJobImportService.SyncReport syncBoard(String provider, String boardKey) {
        ExternalJobConnector connector = connectors.get(provider == null ? "" : provider.trim().toUpperCase());
        if (connector == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connecteur ATS inconnu.");
        }
        if (!connector.providerEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le provider " + connector.label() + " est desactive.");
        }
        try {
            return imports.importSnapshot(connector.sourceCode(), connector.fetchNormalized(boardKey));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    private String readFeed(String location) throws Exception {
        Resource resource = resources.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Flux partenaire introuvable : " + location);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}