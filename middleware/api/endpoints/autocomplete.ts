
import { Router } from 'express';

import * as _ from 'lodash';

import * as api from '../';
import * as api_response from '../response';
import * as speedrun_db from '../../lib/speedrun-db';
import { load_indexer, load_config } from '../../lib/config';

type IndexerResponse = {[type: string]: any[]};

const SCAN_INDEXES: {[type: string]: any} = {
    games: {
        indexer: load_indexer(load_config(), 'games'),
        loc: 'games'
    },

    players: {
        indexer: load_indexer(load_config(), 'players'),
        loc: 'players'
    }
};

const router = Router();

router.get('/', async (req, res) => {
    let query = <string>req.query.q;

    if(!query || query.length > api.config!.api.maxSearchLength)
        api_response.error(res, api_response.err.INVALID_PARAMS(['q'], 'invalid length'));

    query = query.toLowerCase();

    // search all the indexer indexes
    try {
        let results: IndexerResponse = {};

        for(let si in SCAN_INDEXES) {
            let ids = await SCAN_INDEXES[si].indexer.search_raw(query, {maxResults: 20});

            if(ids.length) {
                // resolve all the results
                let raw = await api.storedb!.hmget(
                    speedrun_db.locs[SCAN_INDEXES[si].loc],
                    ...ids);
                
                results[si] = _.chain(raw)
                    .reject(_.isNil)
                    .map(JSON.parse)
                    .value();
            }
        }

        return api_response.custom(res, {
            search: results
        });
    }
    catch(err) {
        console.log('api/autocomplete: could not autocompleted:', err);
        api_response.error(res, api_response.err.INTERNAL_ERROR());
    }
});

module.exports = router;