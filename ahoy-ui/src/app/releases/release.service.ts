/*
 * Copyright  2021 LSD Information Technology (Pty) Ltd
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {map, tap} from 'rxjs/operators';
import {LoggerService} from '../util/logger.service';
import {RestClientService} from '../util/rest-client.service';
import {Release, ReleaseVersion} from './release';

@Injectable({
  providedIn: 'root'
})
export class ReleaseService {

  constructor(
    private log: LoggerService,
    private restClient: RestClientService) {
  }

  getAll(): Observable<Release[]> {
    const url = `/data/releases?sort=id`;
    return this.restClient.get<any>(url).pipe(
      map(response => response._embedded.releases as Release[]),
      tap((releases) => this.log.debug(`fetched ${releases.length} releases`))
    );
  }

  getAllSummary(): Observable<Release[]> {
    const url = `/data/releases?projection=releaseSummary&sort=id`;
    return this.restClient.get<any>(url).pipe(
      map(response => response._embedded.releases as Release[]),
      tap((releases) => this.log.debug(`fetched ${releases.length} releases`))
    );
  }

  getAllForAdd(environmentIdToIgnore: number): Observable<Release[]> {
    const url = `/data/releases/search/forAdd?environmentId=${environmentIdToIgnore}&projection=release`;
    return this.restClient.get<any>(url).pipe(
      map(response => response._embedded.releases as Release[]),
      tap((releases) => this.log.debug(`fetched ${releases.length} releases for add`))
    );
  }

  // TODO: the url seems wrong, fix or delete method
  getAllByEnvironment(environmentId: number): Observable<Release[]> {
    const url = `/data/environments/${environmentId}/releases`;
    return this.restClient.get<any>(url).pipe(
      map(response => response._embedded.releases as Release[]),
      tap((releases) => this.log.debug(`fetched ${releases.length} releases for environment=${environmentId}`))
    );
  }

  get(id: number): Observable<Release> {
    const url = `/data/releases/${id}`;
    return this.restClient.get<Release>(url).pipe(
      tap((release) => this.log.debug('fetched release', release))
    );
  }

  getVersion(id: number): Observable<ReleaseVersion> {
    const url = `/data/releaseVersions/${id}?projection=releaseVersion`;
    return this.restClient.get<ReleaseVersion>(url).pipe(
      tap((releaseVersion) => this.log.debug('fetched release version', releaseVersion))
    );
  }

  save(release: Release): Observable<Release> {
    if (!release.id) {
      this.log.debug('saving release: ', release);
      return this.restClient.post<Release>('/data/releases', release).pipe(
        tap((rel) => this.log.debug('saved new release', rel))
      );

    } else {
      const url = `/data/releases/${release.id}`;
      return this.restClient.put(url, release).pipe(
        tap((rel) => this.log.debug('updated release', rel))
      );
    }
  }

  delete(release: Release): Observable<Release> {
    const id = release.id;
    const url = `/data/releases/${id}`;

    return this.restClient.delete<Release>(url).pipe(
      tap(() => this.log.debug('deleted release', release))
    );
  }

  saveVersion(releaseVersion: ReleaseVersion): Observable<ReleaseVersion> {
    if (!releaseVersion.id) {
      this.log.debug('saving release version: ', releaseVersion);
      return this.restClient.post<ReleaseVersion>('/data/releaseVersions', releaseVersion).pipe(
        tap((relVersion) => this.log.debug('saved new release version', relVersion))
      );

    } else {
      this.log.debug('saving release version: ', releaseVersion);
      const url = `/data/releaseVersions/${releaseVersion.id}`;
      return this.restClient.put(url, releaseVersion).pipe(
        tap((relVersion) => this.log.debug('updated release version', relVersion))
      );
    }
  }

  associateApplication(releaseVersionId: number, applicationVersionId: number): Observable<string> {
    const linkUrl = `/data/releaseVersions/${releaseVersionId}/applicationVersions`;
    const applicationLink = this.linkApplication(applicationVersionId);
    const headers = {'Content-Type': 'text/uri-list'};

    return this.restClient.patch(linkUrl, applicationLink, headers).pipe(
      tap(() => this.log.debug(
        `associated release version id=${releaseVersionId} with application version id=${applicationVersionId}`))
    );
  }

  removeAssociatedApplication(releaseVersionId: number, applicationVersionId: number): Observable<{}> {
    const linkUrl = `/data/releaseVersions/${releaseVersionId}/applicationVersions/${applicationVersionId}`;

    return this.restClient.delete(linkUrl).pipe(
      tap(() => this.log.debug(
        `removed association between release version id=${releaseVersionId} with application version id=${applicationVersionId}`))
    );
  }

  link(id: number): string {
    return this.restClient.getLink('/data/releases', id);
  }

  linkVersion(id: number): string {
    return this.restClient.getLink('/data/releaseVersions', id);
  }

  linkApplication(id: number): string {
    return this.restClient.getLink('/data/applicationVersions', id);
  }
}
